package com.example.hearhome.ui.space

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hearhome.pet.ActionType
import com.example.hearhome.pet.Pet
import com.example.hearhome.pet.PetViewModel
import kotlinx.coroutines.launch

/**
 * 空间宠物卡片 - 显示宠物状态和操作按钮
 */
@Composable
fun SpacePetCard(
    spaceId: Int,
    petViewModel: PetViewModel,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val petState by petViewModel.petState.collectAsState()
    var isExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(spaceId) {
        petViewModel.initPet(spaceId, "萌宠")
        petViewModel.refreshFromCloud(spaceId)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = petState?.name ?: "空间宠物",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "收起" else "展开"
                    )
                }
            }

            if (isExpanded && petState != null) {
                Spacer(Modifier.height(16.dp))

                // 属性显示
                val attrs = petState!!.attributes
                PetAttributeBar("心情", attrs.mood, Color(0xFFFFB6C1))
                Spacer(Modifier.height(8.dp))
                PetAttributeBar("健康", attrs.health, Color(0xFF90EE90))
                Spacer(Modifier.height(8.dp))
                PetAttributeBar("体力", attrs.energy, Color(0xFFFFD700))
                Spacer(Modifier.height(8.dp))
                PetAttributeBar("水分", attrs.hydration, Color(0xFF87CEEB))
                Spacer(Modifier.height(8.dp))
                PetAttributeBar("亲密度", attrs.intimacy, Color(0xFFFF69B4))

                Spacer(Modifier.height(16.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PetActionButton(
                        icon = Icons.Default.Send,
                        label = "喂食",
                        onClick = {
                            scope.launch {
                                petViewModel.applyAction(ActionType.Feed)
                            }
                        }
                    )
                    PetActionButton(
                        icon = Icons.Default.Info,
                        label = "浇水",
                        onClick = {
                            scope.launch {
                                petViewModel.applyAction(ActionType.Water)
                            }
                        }
                    )
                    PetActionButton(
                        icon = Icons.Default.Add,
                        label = "治疗",
                        onClick = {
                            scope.launch {
                                petViewModel.applyAction(ActionType.Treat)
                            }
                        }
                    )
                    PetActionButton(
                        icon = Icons.Default.Face,
                        label = "玩耍",
                        onClick = {
                            scope.launch {
                                petViewModel.applyAction(ActionType.Play)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PetAttributeBar(
    label: String,
    value: Int,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$value/100",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = value / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun PetActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
