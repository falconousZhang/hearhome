package com.example.hearhome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Emoji选择器组件
 */
@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择表情",
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Emoji分类标签
                var selectedCategory by remember { mutableStateOf(EmojiCategory.SMILEYS) }
                
                ScrollableTabRow(
                    selectedTabIndex = EmojiCategory.values().indexOf(selectedCategory),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EmojiCategory.values().forEach { category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            text = { Text(category.label) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Emoji网格
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(selectedCategory.emojis) { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clickable {
                                    onEmojiSelected(emoji)
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Emoji分类
 */
enum class EmojiCategory(val label: String, val emojis: List<String>) {
    SMILEYS("笑脸", listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
        "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
        "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪",
        "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨",
        "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥"
    )),
    
    GESTURES("手势", listOf(
        "👋", "🤚", "🖐", "✋", "🖖", "👌", "🤏", "✌",
        "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕",
        "👇", "☝", "👍", "👎", "✊", "👊", "🤛", "🤜",
        "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍", "💅"
    )),
    
    EMOTIONS("情绪", listOf(
        "😔", "😕", "🙁", "☹", "😣", "😖", "😫", "😩",
        "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯",
        "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓",
        "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑"
    )),
    
    ANIMALS("动物", listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
        "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
        "🐧", "🐦", "🐤", "🐣", "🦆", "🦅", "🦉", "🦇",
        "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌"
    )),
    
    FOOD("食物", listOf(
        "🍎", "🍏", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓",
        "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅",
        "🍆", "🥑", "🥦", "🥬", "🥒", "🌶", "🌽", "🥕",
        "🍔", "🍕", "🌭", "🥪", "🌮", "🌯", "🥗", "🍜"
    )),
    
    ACTIVITIES("活动", listOf(
        "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
        "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
        "🏏", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋",
        "🎽", "🛹", "🛼", "🛷", "⛸", "🥌", "🎿", "⛷"
    )),
    
    OBJECTS("物品", listOf(
        "⌚", "📱", "📲", "💻", "⌨", "🖥", "🖨", "🖱",
        "🖲", "🕹", "🗜", "💾", "💿", "📀", "📼", "📷",
        "📸", "📹", "🎥", "📽", "🎞", "📞", "☎", "📟",
        "📠", "📺", "📻", "🎙", "🎚", "🎛", "🧭", "⏱"
    )),
    
    SYMBOLS("符号", listOf(
        "❤", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
        "🤎", "💔", "❣", "💕", "💞", "💓", "💗", "💖",
        "💘", "💝", "💟", "☮", "✝", "☪", "🕉", "☸",
        "✡", "🔯", "🕎", "☯", "☦", "🛐", "⛎", "♈"
    ))
}

/**
 * Emoji文本框（带Emoji按钮）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "输入内容",
    placeholder: String = "",
    maxLines: Int = 5,
    minHeight: Int = 100
) {
    var showEmojiPicker by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight.dp),
            maxLines = maxLines,
            trailingIcon = {
                IconButton(onClick = { showEmojiPicker = true }) {
                    Text("😀", fontSize = 20.sp)
                }
            }
        )
    }
    
    if (showEmojiPicker) {
        EmojiPicker(
            onEmojiSelected = { emoji ->
                onValueChange(value + emoji)
            },
            onDismiss = { showEmojiPicker = false }
        )
    }
}
