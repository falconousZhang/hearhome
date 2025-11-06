package com.example.hearhome.data.local

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 空间与创建者信息的组合数据类
 * 用于UI展示
 */
data class SpaceWithCreator(
    @Embedded
    val space: Space,
    
    @Relation(
        parentColumn = "creatorId",
        entityColumn = "uid"
    )
    val creator: User
)

/**
 * 空间成员与用户信息的组合数据类
 */
data class SpaceMemberWithUser(
    @Embedded
    val member: SpaceMember,
    
    @Relation(
        parentColumn = "userId",
        entityColumn = "uid"
    )
    val user: User
)

/**
 * 动态与作者信息的组合数据类
 */
data class PostWithAuthor(
    @Embedded
    val post: SpacePost,
    
    @Relation(
        parentColumn = "authorId",
        entityColumn = "uid"
    )
    val author: User
)

/**
 * 评论与作者信息的组合数据类
 */
data class CommentWithAuthor(
    @Embedded
    val comment: PostComment,
    
    @Relation(
        parentColumn = "authorId",
        entityColumn = "uid"
    )
    val author: User
)
