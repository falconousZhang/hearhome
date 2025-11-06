package com.example.hearhome.ui.space

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.*
import com.example.hearhome.utils.NotificationHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 空间动态 ViewModel
 * 处理空间内动态的发布、点赞、评论、收藏等
 */
class SpacePostViewModel(
    private val spacePostDao: SpacePostDao,
    private val userDao: UserDao,
    private val postFavoriteDao: PostFavoriteDao,
    private val spaceId: Int,
    private val currentUserId: Int,
    private val context: Context? = null
) : ViewModel() {

    // 空间内的所有动态
    private val _posts = MutableStateFlow<List<PostWithAuthorInfo>>(emptyList())
    val posts: StateFlow<List<PostWithAuthorInfo>> = _posts.asStateFlow()

    // 选中的动态详情
    private val _selectedPost = MutableStateFlow<PostWithAuthorInfo?>(null)
    val selectedPost: StateFlow<PostWithAuthorInfo?> = _selectedPost.asStateFlow()

    // 当前选中的 postId
    private val _selectedPostId = MutableStateFlow<Int?>(null)

    // 选中动态的评论列表
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val comments: StateFlow<List<CommentInfo>> = _selectedPostId.flatMapLatest { postId ->
        if (postId == null) {
            flowOf(emptyList())
        } else {
            spacePostDao.getPostComments(postId).map { commentList ->
                commentList.mapNotNull { comment ->
                    val author = userDao.getUserById(comment.authorId)
                    val replyToUser = comment.replyToUserId?.let {
                        userDao.getUserById(it)
                    }
                    if (author != null) {
                        CommentInfo(comment, author, replyToUser)
                    } else null
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


    init {
        loadPosts()
    }

    /**
     * 加载空间动态
     */
    fun loadPosts() {
        viewModelScope.launch {
            spacePostDao.getSpacePosts(spaceId).collect { postList ->
                val postsWithInfo = postList.map { post ->
                    val author = userDao.getUserById(post.authorId)
                    val hasLiked = spacePostDao.hasLiked(post.id, currentUserId) > 0
                    val hasFavorited = postFavoriteDao.isFavorited(currentUserId, post.id) > 0
                    PostWithAuthorInfo(post, author, hasLiked, hasFavorited)
                }
                _posts.value = postsWithInfo
            }
        }
    }

    /**
     * 发布新动态
     */
    suspend fun createPost(
    content: String,
    imageUris: List<String>? = null,
    location: String? = null
): Boolean {
    return try {
        val post = SpacePost(
            spaceId = spaceId,
            authorId = currentUserId,
            content = content,
            images = imageUris?.joinToString(","),
            location = location
        )
        spacePostDao.createPost(post)
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
            spacePostDao.deletePost(postId)
            loadPosts()
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
            
            loadPosts()
            selectPost(postId) // Refresh selected post
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
        viewModelScope.launch {
            val post = spacePostDao.getPostById(postId)
            if (post != null) {
                val author = userDao.getUserById(post.authorId)
                val hasLiked = spacePostDao.hasLiked(post.id, currentUserId) > 0
                _selectedPost.value = PostWithAuthorInfo(post, author, hasLiked)
            }
        }
    }

    /**
     * 发布评论
     */
    suspend fun addComment(
        postId: Int,
        content: String,
        replyToUserId: Int? = null,
        audioPath: String? = null,
        audioDuration: Long? = null
    ): Boolean {
        return try {
            val comment = PostComment(
                postId = postId,
                authorId = currentUserId,
                content = content,
                audioPath = audioPath,
                audioDuration = audioDuration,
                replyToUserId = replyToUserId
            )
            spacePostDao.addCommentWithCount(comment)
            
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
            
            // 更新帖子列表中的评论数
            loadPosts()
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
            spacePostDao.deleteComment(commentId)
            spacePostDao.updateCommentCount(postId, -1)
            loadPosts()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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
            loadPosts()
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
                        PostWithAuthorInfo(post, author, hasLiked, hasFavorited = true)
                    } else null
                }
                _posts.value = favoritePosts
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
    val hasFavorited: Boolean = false
)

/**
 * 评论及作者信息
 */
data class CommentInfo(
    val comment: PostComment,
    val author: User,
    val replyToUser: User? = null
)
