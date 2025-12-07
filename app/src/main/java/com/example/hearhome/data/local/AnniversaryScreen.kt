@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.hearhome.data.local

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hearhome.utils.AnniversaryReminder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AnniversaryScreen(
    spaceId: Int,
    currentUserId: Int,
    partnerUserId: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val dao = remember { db.anniversaryDao() }

    val vm: AnniversaryLiteViewModel = viewModel(
        factory = AnniversaryLiteViewModelFactory(dao, spaceId, currentUserId)
    )
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(spaceId) { vm.load() } // 进入页面即加载

    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("纪念日") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新增纪念日")
                    }
                }
            )
        }
    ) { paddings ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddings)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有纪念日，点击右上角 + 新建吧")
                }
            } else {
                uiState.items.forEach { ann ->
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        ann.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                            .format(ann.dateMillis),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (ann.status == "pending" && ann.creatorUserId != currentUserId) {
                                        FilledTonalButton(onClick = {
                                            vm.confirm(ann)
                                            val c = Calendar.getInstance().apply { timeInMillis = ann.dateMillis }
                                            AnniversaryReminder.scheduleYearlyExact(
                                                context = context,
                                                anniversaryId = ann.id,
                                                spaceId = spaceId,
                                                month = c.get(Calendar.MONTH),
                                                day = c.get(Calendar.DAY_OF_MONTH),
                                                hour = c.get(Calendar.HOUR_OF_DAY),
                                                minute = c.get(Calendar.MINUTE)
                                            )
                                        }) { Icon(Icons.Default.Done, null); Spacer(Modifier.width(6.dp)); Text("确认") }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    IconButton(onClick = { vm.delete(ann) }) {
                                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "样式：${ann.style}（${ann.status}）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateAnniversaryDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, dateMillis, style ->
                vm.create(name, dateMillis, style) { newId ->
                    val c = Calendar.getInstance().apply { timeInMillis = dateMillis }
                    AnniversaryReminder.scheduleYearlyExact(
                        context = context,
                        anniversaryId = newId,
                        spaceId = spaceId,
                        month = c.get(Calendar.MONTH),
                        day = c.get(Calendar.DAY_OF_MONTH),
                        hour = c.get(Calendar.HOUR_OF_DAY),
                        minute = c.get(Calendar.MINUTE)
                    )
                    showCreate = false
                }
            }
        )
    }
}

/* ---------- 新建弹窗 ---------- */

@Composable
private fun CreateAnniversaryDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, dateMillis: Long, style: String) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    val styleOptions = listOf(
        "simple" to "简洁",
        "ring" to "环形",
        "card" to "卡片",
        "flip" to "翻牌",
        "capsule" to "胶囊进度"
    )
    var styleMenu by remember { mutableStateOf(false) }
    var stylePair by remember { mutableStateOf(styleOptions.first()) }

    // 使用 Material 3 的 DatePicker 和 TimePicker 状态
    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()
    
    // 控制日期和时间选择器的显示
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    // 用于显示的选中时间文本
    val selectedDateText = datePickerState.selectedDateMillis?.let {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it)
    } ?: "选择日期"
    
    val selectedTimeText = String.format(Locale.getDefault(), "%02d:%02d", timePickerState.hour, timePickerState.minute)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建纪念日") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("名称（如：相恋纪念日）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 日期选择按钮
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "日期: $selectedDateText")
                }

                // 时间选择按钮
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "时间: $selectedTimeText")
                }

                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { styleMenu = true }
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) { Text("倒计时样式：${stylePair.second}") }

                    DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                        styleOptions.forEach { opt ->
                            DropdownMenuItem(text = { Text(opt.second) }, onClick = {
                                stylePair = opt; styleMenu = false
                            })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = nameText.isNotBlank() && datePickerState.selectedDateMillis != null,
                onClick = {
                    val dateMillis = datePickerState.selectedDateMillis ?: return@Button
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = dateMillis
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onCreate(nameText, calendar.timeInMillis, stylePair.first)
                }
            ) { Text("保存（待确认）") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    // 日期选择弹窗
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 时间选择弹窗
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}
