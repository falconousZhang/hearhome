package com.example.hearhome.ui.space

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.*
import com.example.hearhome.model.AttachmentType
import com.example.hearhome.model.ResolvedAttachment
import com.example.hearhome.utils.AudioUtils
import com.example.hearhome.utils.ImageUtils
import com.example.hearhome.utils.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray

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

    // 当前选中的 postId
    private val _selectedPostId = MutableStateFlow<Int?>(null)
    
    // 空间内的所有动态（使用响应式Flow，类似comments的实现）
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val posts: StateFlow<List<PostWithAuthorInfo>> = spacePostDao.getSpacePosts(spaceId)
        .flatMapLatest { postList ->
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
                            val hasFavorited = postFavoriteDao.isFavorited(currentUserId, post.id) > 0
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

    /**
     * 发布新动态
     */
    suspend fun createPost(
        content: String,
        attachments: List<ResolvedAttachment> = emptyList(),
        location: String? = null
    ): Boolean {
        return try {
            val imageUris = attachments
                .filter { it.type == AttachmentType.IMAGE }
                .map { it.uri }
            val imagesJson = if (imageUris.isNotEmpty()) JSONArray(imageUris).toString() else null

            val post = SpacePost(
                spaceId = spaceId,
                authorId = currentUserId,
                content = content,
                images = imagesJson,
                location = location
            )

            val postId = spacePostDao.createPost(post).toInt()

            if (postId > 0 && attachments.isNotEmpty()) {
                val entities = attachments.map {
                    MediaAttachment(
                        ownerType = AttachmentOwnerType.SPACE_POST,
                        ownerId = postId,
                        type = it.type.name,
                        uri = it.uri,
                        duration = it.duration
                    )
                }
                mediaAttachmentDao.insertAttachments(entities)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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
