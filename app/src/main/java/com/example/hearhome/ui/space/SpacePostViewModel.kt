package com.example.hearhome.ui.space

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.*
import com.example.hearhome.data.remote.ApiService
import com.example.hearhome.data.remote.ApiSpacePost
import com.example.hearhome.data.remote.SpacePostUpdate
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.*
import com.example.hearhome.model.AttachmentType
import com.example.hearhome.model.ResolvedAttachment
import com.example.hearhome.utils.AudioUtils
import com.example.hearhome.utils.ImageUtils
import com.example.hearhome.utils.NotificationHelper
import com.example.hearhome.ui.chat.ImageUploadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.io.File

/**
 * 空间动态 ViewModel
 * 处理空间内动态的发布、点赞、评论、收藏等
 */
class SpacePostViewModel(
    private val spacePostDao: SpacePostDao,
    private val userDao: UserDao,
    private val postFavoriteDao: PostFavoriteDao,
    private val mediaAttachmentDao: MediaAttachmentDao,
    private val spaceId: Int,
    private val currentUserId: Int,
    private val context: Context? = null
) : ViewModel() {

    private val websocketJson = Json { ignoreUnknownKeys = true }
    private var realtimeJob: Job? = null
    private var refreshJob: Job? = null

    // 当前选中的 postId
    private val _selectedPostId = MutableStateFlow<Int?>(null)

    // 空间内的所有动态（使用响应式Flow，类似comments的实现）
    // 同时监听动态列表、附件变化和收藏状态变化
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val posts: StateFlow<List<PostWithAuthorInfo>> = combine(
        spacePostDao.getSpacePosts(spaceId),
        postFavoriteDao.observeUserFavoritedPostIds(currentUserId)
    ) { postList, favoritedPostIds ->
        Pair(postList, favoritedPostIds.toSet())
    }.flatMapLatest { (postList, favoritedPostIds) ->
        if (postList.isEmpty()) {
            flowOf(emptyList())
        } else {
            val postIds = postList.map { it.id }
            mediaAttachmentDao
                .observeAttachmentsForOwners(
                    AttachmentOwnerType.SPACE_POST,
                    postIds
                )
                .mapLatest { attachments ->
                    val attachmentsMap = attachments.groupBy { it.ownerId }
                    postList.map { post ->
                        val author = userDao.getUserById(post.authorId)
                        val hasLiked = spacePostDao.hasLiked(post.id, currentUserId) > 0
                        val hasFavorited = post.id in favoritedPostIds
                        PostWithAuthorInfo(
                            post = post,
                            author = author,
                            hasLiked = hasLiked,
                            hasFavorited = hasFavorited,
                            attachments = attachmentsMap[post.id].orEmpty()
                        )
                    }
                }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 收藏的动态列表
    private val _favoritePosts = MutableStateFlow<List<PostWithAuthorInfo>>(emptyList())
    val favoritePosts: StateFlow<List<PostWithAuthorInfo>> = _favoritePosts.asStateFlow()

    init {
        // 进入空间时先从服务器同步一次动态和作者信息
        viewModelScope.launch { syncPostsFromServer() }
        startRealtimeUpdates()
    }

    // 选中的动态详情（从posts中获取）
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedPost: StateFlow<PostWithAuthorInfo?> = _selectedPostId
        .flatMapLatest { postId ->
            if (postId == null) {
                flowOf(null)
            } else {
                posts.map { postList ->
                    postList.firstOrNull { it.post.id == postId }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // 选中动态的评论列表
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val comments: StateFlow<List<CommentInfo>> = _selectedPostId.flatMapLatest { postId ->
        if (postId == null) {
            flowOf(emptyList())
        } else {
            spacePostDao.getPostComments(postId).flatMapLatest { commentList ->
                if (commentList.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val commentIds = commentList.map { it.id }
                    mediaAttachmentDao
                        .observeAttachmentsForOwners(
                            AttachmentOwnerType.POST_COMMENT,
                            commentIds
                        )
                        .mapLatest { attachments ->
                            val attachmentsMap = attachments.groupBy { it.ownerId }
                            commentList.mapNotNull { comment ->
                                val author = userDao.getUserById(comment.authorId)
                                val replyToUser = comment.replyToUserId?.let { userDao.getUserById(it) }
                                if (author != null) {
                                    CommentInfo(
                                        comment = comment,
                                        author = author,
                                        replyToUser = replyToUser,
                                        attachments = attachmentsMap[comment.id].orEmpty()
                                    )
                                } else {
                                    null
                                }
                            }
                        }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun startRealtimeUpdates() {
        if (realtimeJob != null) return

        realtimeJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                var session: DefaultClientWebSocketSession? = null
                try {
                    session = ApiService.connectPostUpdates(spaceId)
                    for (frame in session.incoming) {
                        if (!isActive) break
                        val text = (frame as? Frame.Text)?.readText() ?: continue
                        val event = runCatching {
                            websocketJson.decodeFromString<SpacePostUpdate>(text)
                        }.getOrNull() ?: continue

                        if (event.post.spaceId != spaceId) continue

                        val apiPost = event.post
                        spacePostDao.insert(apiPost.toLocal())

                        val imageUrls = parseImages(apiPost.images)
                        if (imageUrls.isNotEmpty()) {
                            mediaAttachmentDao.deleteAttachments(AttachmentOwnerType.SPACE_POST, apiPost.id)
                            val entities = imageUrls.map { url ->
                                MediaAttachment(
                                    ownerType = AttachmentOwnerType.SPACE_POST,
                                    ownerId = apiPost.id,
                                    type = AttachmentType.IMAGE.name,
                                    uri = url
                                )
                            }
                            mediaAttachmentDao.insertAttachments(entities)
                        }
                    }
                } catch (e: Exception) {
                    println("[SpacePostViewModel] Realtime stream error: ${e.message}")
                    delay(5000)
                } finally {
                    runCatching {
                        session?.close(CloseReason(CloseReason.Codes.NORMAL, "close"))
                    }
                }
            }
        }
    }

    /**
     * 轮询刷新动态列表（无需改后端），默认 5 秒一次。
     */
    fun startAutoRefresh(intervalMs: Long = 5_000) {
        if (refreshJob != null) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                syncPostsFromServer()
                delay(intervalMs)
            }
        }
    }

    /**
     * 发布新动态
     * @return 新创建的动态ID，失败返回-1
     */
    suspend fun createPost(
        content: String,
        attachments: List<ResolvedAttachment> = emptyList(),
        location: String? = null
    ): Long {
        return try {
            // 必须有文字或图片，否则不发
            if (content.isBlank() && attachments.isEmpty()) return -1L

            // 先上传图片附件，拿到可共享的 URL，再随帖子一并下发
            val uploadedImageUrls = mutableListOf<String>()
            val imageUriToRemote = mutableMapOf<String, String>()
            val imageAttachments = attachments.filter { it.type == AttachmentType.IMAGE }
            val ctx = context
            if (ctx != null) {
                imageAttachments.forEachIndexed { index, attachment ->
                    val bytes = loadBytes(ctx, attachment.uri)
                    if (bytes != null) {
                        val resp = runCatching {
                            ApiService.uploadImage(bytes, "post_${System.currentTimeMillis()}_${index}.jpg")
                        }.getOrNull()
                        if (resp != null && resp.status.value in 200..299) {
                            val remoteUrl = runCatching {
                                resp.body<ImageUploadResponse>().imageUrl
                            }.getOrElse {
                                // 兼容老格式 {"imageUrl":"xxx"}
                                runCatching { resp.body<Map<String, String>>() }.getOrNull()?.get("imageUrl")
                            }

                            if (!remoteUrl.isNullOrBlank()) {
                                uploadedImageUrls.add(remoteUrl)
                                imageUriToRemote[attachment.uri] = remoteUrl
                            } else {
                                println("[uploadImage] parse imageUrl failed, raw=${resp.bodyAsText()}")
                            }
                        } else {
                            println("[uploadImage] status=${resp?.status?.value} resp=${resp?.bodyAsText()}")
                        }
                    } else {
                        println("[uploadImage] loadBytes failed for ${attachment.uri}")
                    }
                }
            }

            // 聊天同款策略：带图必须上传成功，否则终止发送；无图则允许纯文字
            if (imageAttachments.isNotEmpty() && uploadedImageUrls.isEmpty()) {
                println("[createPost] all image uploads failed, abort")
                return -1L
            }

            val imagesJson = if (uploadedImageUrls.isNotEmpty()) JSONArray(uploadedImageUrls).toString() else null

            // 先向服务器创建动态，保证其他成员可见
            val apiPost = ApiSpacePost(
                spaceId = spaceId,
                authorId = currentUserId,
                content = content,
                images = imagesJson,
                location = location,
                timestamp = System.currentTimeMillis()
            )
            val response = ApiService.createPost(apiPost)

            val createdPost: ApiSpacePost? = if (response.status.value in 200..299) {
                runCatching { response.body<ApiSpacePost>() }.getOrNull()
            } else null

            val localPost = (createdPost ?: apiPost.copy(id = 0))
                .toLocal()
                .copy(authorId = currentUserId) // 确保作者ID一致

            val postId = spacePostDao.insert(localPost)

            // 保存附件到统一的 MediaAttachment 表（图片优先用远端 URL，便于重登/他端显示）
            if (postId > 0 && attachments.isNotEmpty()) {
                val entities = attachments.mapNotNull { att ->
                    val finalUri = if (att.type == AttachmentType.IMAGE) {
                        imageUriToRemote[att.uri]
                    } else att.uri
                    if (finalUri.isNullOrBlank()) null else MediaAttachment(
                        ownerType = AttachmentOwnerType.SPACE_POST,
                        ownerId = (createdPost?.id ?: postId.toInt()),
                        type = att.type.name,
                        uri = finalUri,
                        duration = att.duration
                    )
                }
                mediaAttachmentDao.insertAttachments(entities)
            }

            // 成功后再同步一次，确保列表最新
            viewModelScope.launch { syncPostsFromServer() }

            if (createdPost != null) createdPost.id.toLong() else postId
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    /**
     * 删除动态
     */
    suspend fun deletePost(postId: Int): Boolean {
        return try {
            val postAttachments = mediaAttachmentDao.getAttachments(AttachmentOwnerType.SPACE_POST, postId)
            if (postAttachments.isNotEmpty()) {
                cleanupAttachmentFiles(postAttachments)
                mediaAttachmentDao.deleteAttachments(AttachmentOwnerType.SPACE_POST, postId)
            }

            // 删除评论附件
            val comments = spacePostDao.getPostCommentsOnce(postId)
            if (comments.isNotEmpty()) {
                comments.forEach { comment ->
                    val commentAttachments = mediaAttachmentDao.getAttachments(
                        AttachmentOwnerType.POST_COMMENT,
                        comment.id
                    )
                    if (commentAttachments.isNotEmpty()) {
                        cleanupAttachmentFiles(commentAttachments)
                        mediaAttachmentDao.deleteAttachments(
                            AttachmentOwnerType.POST_COMMENT,
                            comment.id
                        )
                    }
                }
            }

            spacePostDao.deletePost(postId)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 切换点赞状态
     */
    suspend fun toggleLike(postId: Int): Boolean {
        return try {
            spacePostDao.toggleLike(postId, currentUserId)

            // 发送点赞通知
            context?.let { ctx ->
                val post = spacePostDao.getPostById(postId)
                if (post != null && post.authorId != currentUserId) {
                    // 只在点赞别人的动态时发送通知
                    val currentUser = userDao.getUserById(currentUserId)
                    val postAuthor = userDao.getUserById(post.authorId)

                    if (currentUser != null && postAuthor != null) {
                        // 检查是否新增点赞（如果已点赞则是取消点赞）
                        val isLiking = spacePostDao.hasLiked(postId, currentUserId) > 0

                        if (isLiking) {
                            val contentPreview = if (post.content.length > 20) {
                                post.content.substring(0, 20) + "..."
                            } else {
                                post.content
                            }

                            NotificationHelper.sendLikeNotification(
                                context = ctx,
                                notificationId = System.currentTimeMillis().toInt(),
                                userName = currentUser.nickname,
                                contentPreview = contentPreview
                            )
                        }
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 选择某条动态查看详情
     */
    fun selectPost(postId: Int) {
        _selectedPostId.value = postId
    }

    /**
     * 发布评论
     */
    suspend fun addComment(
        postId: Int,
        content: String,
        replyToUserId: Int? = null,
        attachments: List<ResolvedAttachment> = emptyList()
    ): Boolean {
        return try {
            val audioAttachment = attachments.firstOrNull { it.type == AttachmentType.AUDIO }
            val comment = PostComment(
                postId = postId,
                authorId = currentUserId,
                content = content,
                audioPath = audioAttachment?.uri,
                audioDuration = audioAttachment?.duration,
                replyToUserId = replyToUserId
            )
            val commentId = spacePostDao.addCommentWithCount(comment).toInt()

            if (commentId > 0 && attachments.isNotEmpty()) {
                val entities = attachments.map {
                    MediaAttachment(
                        ownerType = AttachmentOwnerType.POST_COMMENT,
                        ownerId = commentId,
                        type = it.type.name,
                        uri = it.uri,
                        duration = it.duration
                    )
                }
                mediaAttachmentDao.insertAttachments(entities)
            }

            // 发送评论通知
            context?.let { ctx ->
                val post = spacePostDao.getPostById(postId)
                if (post != null && post.authorId != currentUserId) {
                    // 只在评论别人的动态时发送通知
                    val currentUser = userDao.getUserById(currentUserId)

                    if (currentUser != null) {
                        NotificationHelper.sendCommentNotification(
                            context = ctx,
                            notificationId = System.currentTimeMillis().toInt(),
                            userName = currentUser.nickname,
                            content = content,
                            postId = postId
                        )
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun syncPostsFromServer() {
        try {
            val remotePostsResponse = ApiService.getPosts(spaceId)
            val remotePosts: List<ApiSpacePost> = runCatching { remotePostsResponse.body<List<ApiSpacePost>>() }.getOrElse { emptyList() }
            if (remotePosts.isEmpty()) return

            // 先存作者信息，避免显示未知用户
            val authorIds = remotePosts.map { it.authorId }.distinct()
            for (uid in authorIds) {
                if (userDao.getUserById(uid) == null) {
                    try {
                        val profileResp = ApiService.getProfile(uid)
                        if (profileResp.status.value in 200..299) {
                            val user = profileResp.body<User>()
                            userDao.insert(user)
                        }
                    } catch (e: Exception) {
                        println("[ERROR] syncPostsFromServer: fetch user $uid failed: ${e.message}")
                    }
                }
            }

            // 写入帖子
            for (apiPost in remotePosts) {
                spacePostDao.insert(apiPost.toLocal())

                // 把服务端 images 字段转换成本地附件，保证换设备/重登仍能看到图片
                val imageUrls = parseImages(apiPost.images)
                if (imageUrls.isNotEmpty()) {
                    mediaAttachmentDao.deleteAttachments(AttachmentOwnerType.SPACE_POST, apiPost.id)
                    val entities = imageUrls.map { url ->
                        MediaAttachment(
                            ownerType = AttachmentOwnerType.SPACE_POST,
                            ownerId = apiPost.id,
                            type = AttachmentType.IMAGE.name,
                            uri = url
                        )
                    }
                    mediaAttachmentDao.insertAttachments(entities)
                }
            }
        } catch (e: Exception) {
            println("[ERROR] syncPostsFromServer failed: ${e.message}")
        }
    }

    private fun ApiSpacePost.toLocal(): SpacePost =
        SpacePost(
            id = this.id,
            spaceId = this.spaceId,
            authorId = this.authorId,
            content = this.content,
            images = this.images,
            location = this.location,
            timestamp = this.timestamp,
            likeCount = this.likeCount,
            commentCount = this.commentCount,
            status = this.status
        )

    private fun loadBytes(ctx: Context, uriString: String): ByteArray? {
        return runCatching {
            // 先直接按文件路径读取（适配我们内部存储下的绝对路径）
            val asFile = File(uriString)
            if (asFile.exists()) {
                return@runCatching asFile.readBytes()
            }

            // 若不是文件路径，再尝试按 Uri 读取
            val uri = Uri.parse(uriString)
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.onFailure {
            println("[loadBytes] failed for $uriString : ${it.message}")
        }.getOrNull()
    }

    private fun parseImages(images: String?): List<String> {
        if (images.isNullOrBlank()) return emptyList()
        val trimmed = images.trim()
        return if (trimmed.startsWith("[")) {
            runCatching {
                val arr = JSONArray(trimmed)
                buildList {
                    for (i in 0 until arr.length()) {
                        val raw = arr.optString(i)
                        val cleaned = raw.trim().trim('"')
                        if (cleaned.isNotEmpty()) add(cleaned)
                    }
                }
            }.getOrElse { parseCommaSeparatedImages(trimmed) }
        } else parseCommaSeparatedImages(trimmed)
    }

    private fun parseCommaSeparatedImages(value: String): List<String> =
        value.split(",").map { it.trim().trim('"') }.filter { it.isNotEmpty() }

    /**
     * 删除评论
     */
    suspend fun deleteComment(commentId: Int, postId: Int): Boolean {
        return try {
            val attachments = mediaAttachmentDao.getAttachments(
                AttachmentOwnerType.POST_COMMENT,
                commentId
            )
            if (attachments.isNotEmpty()) {
                cleanupAttachmentFiles(attachments)
                mediaAttachmentDao.deleteAttachments(
                    AttachmentOwnerType.POST_COMMENT,
                    commentId
                )
            }

            spacePostDao.deleteComment(commentId)
            spacePostDao.updateCommentCount(postId, -1)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun cleanupAttachmentFiles(attachments: List<MediaAttachment>) {
        attachments.forEach { attachment ->
            when (AttachmentType.fromStorage(attachment.type)) {
                AttachmentType.IMAGE -> ImageUtils.deleteImage(attachment.uri)
                AttachmentType.AUDIO -> AudioUtils.deleteAudio(attachment.uri)
            }
        }
    }

    /**
     * 切换收藏状态
     */
    suspend fun toggleFavorite(postId: Int): Boolean {
        return try {
            val isFavorited = postFavoriteDao.isFavorited(currentUserId, postId) > 0
            if (isFavorited) {
                // 取消收藏
                postFavoriteDao.removeFavorite(currentUserId, postId)
            } else {
                // 添加收藏
                val favorite = PostFavorite(
                    userId = currentUserId,
                    postId = postId
                )
                postFavoriteDao.addFavorite(favorite)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 获取用户的收藏动态列表
     */
    fun loadUserFavorites() {
        viewModelScope.launch {
            postFavoriteDao.getUserFavoritePostIds(currentUserId).collect { postIds ->
                val favoritePosts = postIds.mapNotNull { postId ->
                    val post = spacePostDao.getPostById(postId)
                    if (post != null) {
                        val author = userDao.getUserById(post.authorId)
                        val hasLiked = spacePostDao.hasLiked(post.id, currentUserId) > 0
                        val attachments = mediaAttachmentDao.getAttachments(
                            AttachmentOwnerType.SPACE_POST,
                            post.id
                        )
                        PostWithAuthorInfo(
                            post = post,
                            author = author,
                            hasLiked = hasLiked,
                            hasFavorited = true,
                            attachments = attachments
                        )
                    } else null
                }
                _favoritePosts.value = favoritePosts
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
        refreshJob?.cancel()
    }
}

/**
 * 动态及作者信息
 */
data class PostWithAuthorInfo(
    val post: SpacePost,
    val author: User?,
    val hasLiked: Boolean = false,
    val hasFavorited: Boolean = false,
    val attachments: List<MediaAttachment> = emptyList()
)

/**
 * 评论及作者信息
 */
data class CommentInfo(
    val comment: PostComment,
    val author: User,
    val replyToUser: User? = null,
    val attachments: List<MediaAttachment> = emptyList()
)
