package com.example.hearhome.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.hearhome.data.local.AppDatabase
import kotlinx.coroutines.launch

/**
 * 我的收藏界面
 * 显示用户收藏的所有动态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    navController: NavController,
    spaceId: Int,
    currentUserId: Int
) {
    val context = LocalContext.current
    val db = AppDatabase.getInstance(context)
    val scope = rememberCoroutineScope()
    
    // 使用空间动态ViewModel
    val viewModel: SpacePostViewModel = viewModel(
        key = "favorites_$spaceId",
        factory = SpacePostViewModelFactory(
            db.spacePostDao(),
            db.userDao(),
            db.postFavoriteDao(),
            spaceId,
            currentUserId,
            context
        )
    )
    
    val favoritePosts by viewModel.favoritePosts.collectAsState()
    
    // 加载收藏的动态
    LaunchedEffect(currentUserId) {
        viewModel.loadUserFavorites()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("我的收藏")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (favoritePosts.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "还没有收藏任何动态",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击动态上的星标图标即可收藏",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "共 ${favoritePosts.size} 条收藏",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                items(favoritePosts) { postInfo ->
                    PostCard(
                        postInfo = postInfo,
                        currentUserId = currentUserId,
                        onLike = {
                            scope.launch {
                                viewModel.toggleLike(postInfo.post.id)
                            }
                        },
                        onComment = {
                            navController.navigate("post_detail/${postInfo.post.id}/$currentUserId")
                        },
                        onDelete = {
                            scope.launch {
                                viewModel.deletePost(postInfo.post.id)
                            }
                        },
                        onFavorite = {
                            scope.launch {
                                viewModel.toggleFavorite(postInfo.post.id)
                                // 取消收藏后重新加载列表
                                viewModel.loadUserFavorites()
                            }
                        }
                    )
                }
            }
        }
    }
}
