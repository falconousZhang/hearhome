package com.example.hearhome.ui.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hearhome.data.local.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 空间动态 ViewModel
 * 处理空间内动态的发布、点赞、评论等
 */
class SpacePostViewModel(
    private val spacePostDao: SpacePostDao,
    private val userDao: UserDao,
    private val spaceId: Int,
    private val currentUserId: Int
) : ViewModel() {

    // 空间内的所有动态
    private val _posts = MutableStateFlow<List<PostWithAuthorInfo>>(emptyList())
    val posts: StateFlow<List<PostWithAuthorInfo>> = _posts.asStateFlow()

    // 选中的动态详情
    private val _selectedPost = MutableStateFlow<PostWithAuthorInfo?>(null)
    val selectedPost: StateFlow<PostWithAuthorInfo?> = _selectedPost.asStateFlow()

    // 选中动态的评论列表
    private val _comments = MutableStateFlow<List<CommentInfo>>(emptyList())
    val comments: StateFlow<List<CommentInfo>> = _comments.asStateFlow()
    
    // 当前正在查看的 postId
    private var currentPostId: Int? = null
    
    // 评论收集协程的 Job，用于取消之前的收集
    private var commentsJob: kotlinx.coroutines.Job? = null

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
                    PostWithAuthorInfo(post, author, hasLiked)
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
        images: List<String>? = null,
        location: String? = null
    ): Boolean {
        return try {
            val imagesJson = images?.joinToString(",")
            val post = SpacePost(
                spaceId = spaceId,
                authorId = currentUserId,
                content = content,
                images = imagesJson,
                location = location
            )
            spacePostDao.createPost(post)
            loadPosts()
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
            loadPosts()
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
        // 取消之前的评论收集协程
        commentsJob?.cancel()
        
        // 更新当前查看的帖子ID
        currentPostId = postId
        
        // 清空旧的评论列表
        _comments.value = emptyList()
        
        viewModelScope.launch {
            val post = spacePostDao.getPostById(postId)
            if (post != null) {
                val author = userDao.getUserById(post.authorId)
                val hasLiked = spacePostDao.hasLiked(post.id, currentUserId) > 0
                _selectedPost.value = PostWithAuthorInfo(post, author, hasLiked)
            }
        }
        
        // 启动新的评论监听协程
        commentsJob = viewModelScope.launch {
            spacePostDao.getPostComments(postId).collect { commentList ->
                val commentsWithInfo = commentList.mapNotNull { comment ->
                    val author = userDao.getUserById(comment.authorId)
                    val replyToUser = comment.replyToUserId?.let { 
                        userDao.getUserById(it) 
                    }
                    if (author != null) {
                        CommentInfo(comment, author, replyToUser)
                    } else null
                }
                _comments.value = commentsWithInfo
            }
        }
    }

    /**
     * 发布评论
     */
    suspend fun addComment(
        postId: Int,
        content: String,
        replyToUserId: Int? = null
    ): Boolean {
        return try {
            val comment = PostComment(
                postId = postId,
                authorId = currentUserId,
                content = content,
                replyToUserId = replyToUserId
            )
            spacePostDao.addCommentWithCount(comment)
            // Flow 会自动更新评论列表，不需要手动调用 loadComments
            // 但需要更新帖子列表中的评论数
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
            // Flow 会自动更新评论列表
            loadPosts()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        commentsJob?.cancel()
    }
}

/**
 * 动态及作者信息
 */
data class PostWithAuthorInfo(
    val post: SpacePost,
    val author: User?,
    val hasLiked: Boolean = false
)

/**
 * 评论及作者信息
 */
data class CommentInfo(
    val comment: PostComment,
    val author: User,
    val replyToUser: User? = null
)
