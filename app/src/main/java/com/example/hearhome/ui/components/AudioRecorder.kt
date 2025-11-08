package com.example.hearhome.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hearhome.utils.AudioUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 语音录制器组件
 */
@Composable
fun AudioRecorder(
    onAudioRecorded: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableLongStateOf(0L) }
    var audioPath by remember { mutableStateOf<String?>(null) }
    
    // 录音权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 开始录音
            audioPath = AudioUtils.startRecording(context)
            if (audioPath != null) {
                isRecording = true
                
                // 计时器
                scope.launch {
                    while (isRecording) {
                        delay(100)
                        recordingTime += 100
                        
                        // 最长录音60秒
                        if (recordingTime >= 60000) {
                            val path = AudioUtils.stopRecording()
                            if (path != null) {
                                val duration = AudioUtils.getAudioDuration(path)
                                onAudioRecorded(path, duration)
                                onDismiss()
                            }
                            isRecording = false
                        }
                    }
                }
            }
        }
    }
    
    // 录音动画
    val infiniteTransition = rememberInfiniteTransition(label = "recording")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    AlertDialog(
        onDismissRequest = {
            if (isRecording) {
                AudioUtils.cancelRecording()
                isRecording = false
            }
            onDismiss()
        },
        title = { Text(if (isRecording) "录音中..." else "准备录音") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isRecording) {
                    // 录音中动画
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "录音中",
                        modifier = Modifier
                            .size(80.dp)
                            .scale(scale),
                        tint = Color.Red
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 录音时长
                    Text(
                        text = AudioUtils.formatDuration(recordingTime),
                        fontSize = 24.sp,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "最长60秒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "麦克风",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("点击开始按钮开始录音")
                }
            }
        },
        confirmButton = {
            if (isRecording) {
                // 完成录音按钮
                Button(
                    onClick = {
                        val path = AudioUtils.stopRecording()
                        if (path != null) {
                            val duration = AudioUtils.getAudioDuration(path)
                            onAudioRecorded(path, duration)
                            onDismiss()
                        }
                        isRecording = false
                    }
                ) {
                    Icon(Icons.Default.Check, "完成")
                    Spacer(Modifier.width(4.dp))
                    Text("完成")
                }
            } else {
                // 开始录音按钮
                Button(
                    onClick = {
                        // 请求录音权限
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) {
                    Icon(Icons.Default.Mic, "开始")
                    Spacer(Modifier.width(4.dp))
                    Text("开始录音")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isRecording) {
                        AudioUtils.cancelRecording()
                        isRecording = false
                    }
                    onDismiss()
                }
            ) {
                Text("取消")
            }
        }
    )
    
    // 组件销毁时清理资源
    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) {
                AudioUtils.cancelRecording()
            }
        }
    }
}

/**
 * 语音播放器组件
 */
@Composable
fun AudioPlayer(
    audioPath: String,
    duration: Long,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable {
                if (isPlaying) {
                    AudioUtils.stopAudio()
                    isPlaying = false
                    currentPosition = 0
                } else {
                    val success = AudioUtils.playAudio(audioPath) {
                        isPlaying = false
                        currentPosition = 0
                    }
                    if (success) {
                        isPlaying = true
                        
                        // 更新播放进度
                        scope.launch {
                            while (isPlaying && currentPosition < duration) {
                                delay(100)
                                currentPosition += 100
                            }
                        }
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 播放/暂停图标
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 语音图标和时长
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height((12 + it * 4).dp)
                        .background(
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            RoundedCornerShape(2.dp)
                        )
                )
                if (it < 2) Spacer(modifier = Modifier.width(2.dp))
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = if (isPlaying) {
                    "${AudioUtils.formatDuration(currentPosition)} / ${AudioUtils.formatDuration(duration)}"
                } else {
                    AudioUtils.formatDuration(duration)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
    
    // 组件销毁时停止播放
    DisposableEffect(Unit) {
        onDispose {
            if (isPlaying) {
                AudioUtils.stopAudio()
            }
        }
    }
}
