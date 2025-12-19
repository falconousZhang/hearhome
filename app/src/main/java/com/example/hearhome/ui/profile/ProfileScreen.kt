package com.example.hearhome.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.hearhome.data.local.AppDatabase
import com.example.hearhome.ui.auth.AuthViewModel
import com.example.hearhome.ui.components.AppBottomNavigation
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

// ==================== 头像风格定义 ====================

/**
 * 头像风格枚举
 */
enum class AvatarStyle(val displayName: String, val prefix: String) {
    SOLID("纯色", ""),
    GRADIENT("渐变", "gradient:"),
    MOSAIC("马赛克", "mosaic:"),
    DOTS("波点", "dots:"),
    STRIPES("条纹", "stripes:"),
    DIAMOND("菱形", "diamond:")
}

// 扩展的颜色列表 - 更多丰富的颜色
private val avatarColorOptions = listOf(
    // 红色系
    "#FF1744", "#F44336", "#E91E63", "#D50000", "#C51162",
    // 紫色系
    "#9C27B0", "#673AB7", "#7C4DFF", "#AA00FF", "#6200EA",
    // 蓝色系
    "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4", "#0091EA",
    // 青绿色系
    "#009688", "#00897B", "#1DE9B6", "#00E676", "#00C853",
    // 绿色系
    "#4CAF50", "#8BC34A", "#CDDC39", "#76FF03", "#64DD17",
    // 黄橙色系
    "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#FF6D00",
    // 棕灰色系
    "#795548", "#9E9E9E", "#607D8B", "#455A64", "#37474F",
    // 特殊色
    "#000000", "#FFFFFF", "#E040FB", "#40C4FF", "#FFAB40"
)

// 预设的渐变色组合
private val gradientPresets = listOf(
    listOf("#FF512F", "#DD2476"),  // 日落粉
    listOf("#4776E6", "#8E54E9"),  // 紫蓝渐变
    listOf("#00B4DB", "#0083B0"),  // 海洋蓝
    listOf("#11998E", "#38EF7D"),  // 翡翠绿
    listOf("#FC466B", "#3F5EFB"),  // 霓虹紫粉
    listOf("#F857A6", "#FF5858"),  // 珊瑚红
    listOf("#4FACFE", "#00F2FE"),  // 天空蓝
    listOf("#43E97B", "#38F9D7"),  // 薄荷绿
    listOf("#FA709A", "#FEE140"),  // 桃子橙
    listOf("#667EEA", "#764BA2"),  // 薰衣草紫
    listOf("#FF6B6B", "#FEC89A"),  // 暖阳橘
    listOf("#A8EDEA", "#FED6E3"),  // 糖果粉
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    userId: Int
) {
    val profileViewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModelFactory(AppDatabase.getInstance(LocalContext.current).userDao())
    )
    LaunchedEffect(userId) { profileViewModel.loadUser(userId) }
    val uiState by profileViewModel.uiState.collectAsState()
    val updateResult by profileViewModel.updateResult.collectAsState()
    val user = uiState.user

    // 菜单和对话框状态
    var showMenu by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showSecurityQuestionDialog by remember { mutableStateOf(false) }

    var userIdVisible by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    var showEmailCodeDialog by remember { mutableStateOf(false) }
    var emailCodePurpose by remember { mutableStateOf<AuthViewModel.VerificationPurpose?>(null) }
    var emailCodeInput by remember { mutableStateOf("") }
    var codeCountdown by remember { mutableStateOf(0) }
    var codeCountdownKey by remember { mutableStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val authState by authViewModel.authState.collectAsState()

    // 监听头像更新结果
    LaunchedEffect(updateResult) {
        when (val result = updateResult) {
            is ProfileViewModel.UpdateResult.Success -> {
                snackbarHostState.showSnackbar(result.message)
                profileViewModel.clearUpdateResult()
            }
            is ProfileViewModel.UpdateResult.Error -> {
                snackbarHostState.showSnackbar(result.message)
                profileViewModel.clearUpdateResult()
            }
            null -> {}
        }
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthViewModel.AuthState.Error -> {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(state.message)
                authViewModel.onProfileEventConsumed()
            }
            is AuthViewModel.AuthState.UpdateSuccess -> {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("私密问题修改成功")
                showSecurityQuestionDialog = false
                showEmailCodeDialog = false
                emailCodePurpose = null
                emailCodeInput = ""
                codeCountdown = 0
                authViewModel.onProfileEventConsumed()
            }
            is AuthViewModel.AuthState.PasswordUpdateSuccess -> {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("密码修改成功，请重新登录")
                showPasswordDialog = false
                showEmailCodeDialog = false
                emailCodePurpose = null
                emailCodeInput = ""
                codeCountdown = 0
            }
            is AuthViewModel.AuthState.EmailCodeRequired -> {
                if (state.purpose == AuthViewModel.VerificationPurpose.UPDATE_PASSWORD || state.purpose == AuthViewModel.VerificationPurpose.UPDATE_SECURITY_QUESTION) {
                    emailCodePurpose = state.purpose
                    emailCodeInput = ""
                    showEmailCodeDialog = true
                    codeCountdown = 60
                    codeCountdownKey += 1
                    state.reason?.let { reason ->
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(reason)
                    }
                }
            }
            is AuthViewModel.AuthState.EmailCodeSent -> {
                if (state.purpose == AuthViewModel.VerificationPurpose.UPDATE_PASSWORD || state.purpose == AuthViewModel.VerificationPurpose.UPDATE_SECURITY_QUESTION) {
                    emailCodePurpose = state.purpose
                    showEmailCodeDialog = true
                    codeCountdown = 60
                    codeCountdownKey += 1
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar("验证码已发送至邮箱，请查收")
                }
            }
            else -> {}
        }
    }

    LaunchedEffect(codeCountdownKey) {
        while (codeCountdown > 0) {
            delay(1000)
            codeCountdown = (codeCountdown - 1).coerceAtLeast(0)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("个人中心") },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("修改头像") },
                                onClick = {
                                    showMenu = false
                                    showAvatarDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("修改密码") },
                                onClick = {
                                    showMenu = false
                                    showPasswordDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("设置私密问题") },
                                onClick = {
                                    showMenu = false
                                    showSecurityQuestionDialog = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            AppBottomNavigation(
                currentRoute = "profile",
                navController = navController,
                userId = userId
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState.isLoading) {
                Spacer(modifier = Modifier.weight(1f))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.weight(1f))
            } else if (user != null) {
                // 头像（可点击修改）- 使用新的渲染组件
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .clickable { showAvatarDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    StyledAvatar(
                        avatarData = user.avatarColor,
                        size = 100.dp,
                        initial = user.nickname.firstOrNull()?.uppercase() ?: "U"
                    )
                }
                Text(
                    text = "点击修改头像",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                Text("个人信息", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))

                InfoCard(label = "昵称", value = user.nickname.ifBlank { "(未设置)" })
                InfoCard(label = "性别", value = user.gender)
                InfoCard(label = "邮箱", value = user.email)
                InfoCard(
                    label = "用户ID",
                    value = if (userIdVisible) user.uid.toString() else "********",
                    trailingIcon = {
                        IconButton(onClick = { userIdVisible = !userIdVisible }) {
                            Icon(
                                imageVector = if (userIdVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "切换ID可见性"
                            )
                        }
                    }
                )
                InfoCard(label = "关系状态", value = when(user.relationshipStatus) {
                    "single" -> "单身"
                    "in_relationship" -> "恋爱中"
                    else -> user.relationshipStatus
                })
                
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { showLogoutDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("退出登录") }

            } else {
                Spacer(modifier = Modifier.weight(1f))
                Text(uiState.error ?: "无法加载用户信息。")
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        
        // 修改头像对话框
        if (showAvatarDialog && user != null) {
            AvatarStyleDialog(
                currentAvatarData = user.avatarColor,
                userInitial = user.nickname.firstOrNull()?.uppercase() ?: "U",
                onDismiss = { showAvatarDialog = false },
                onAvatarSelected = { newAvatarData ->
                    profileViewModel.updateAvatarColor(userId, newAvatarData)
                    showAvatarDialog = false
                }
            )
        }
        
        // 修改密码对话框
        if (showPasswordDialog && user != null) {
            ChangePasswordDialog(
                email = user.email,
                authViewModel = authViewModel,
                authState = authState,
                onDismiss = { showPasswordDialog = false }
            )
        }
        
        // 设置私密问题对话框
        if (showSecurityQuestionDialog && user != null) {
            SecurityQuestionDialog(
                email = user.email,
                currentQuestion = user.secQuestion,
                authViewModel = authViewModel,
                authState = authState,
                onDismiss = { showSecurityQuestionDialog = false }
            )
        }
        
        if (showLogoutDialog) {
            LogoutConfirmationDialog(
                onConfirm = {
                    showLogoutDialog = false
                    authViewModel.logout()
                },
                onDismiss = { showLogoutDialog = false }
            )
        }

        if (showEmailCodeDialog && emailCodePurpose != null && user != null) {
            EmailCodeDialog(
                email = user.email,
                purpose = emailCodePurpose!!,
                code = emailCodeInput,
                onCodeChange = { emailCodeInput = it },
                onConfirm = { authViewModel.verifyEmailCode(emailCodeInput.trim()) },
                onResend = { authViewModel.resendEmailCode(emailCodePurpose!!) },
                onDismiss = {
                    showEmailCodeDialog = false
                    emailCodeInput = ""
                    codeCountdown = 0
                },
                isLoading = authState is AuthViewModel.AuthState.Loading,
                codeCountdown = codeCountdown
            )
        }
    }
}

// ==================== 头像渲染组件 ====================

/**
 * 解析头像数据格式
 */
private fun parseAvatarData(avatarData: String): Pair<AvatarStyle, String> {
    return when {
        avatarData.startsWith("gradient:") -> AvatarStyle.GRADIENT to avatarData.removePrefix("gradient:")
        avatarData.startsWith("mosaic:") -> AvatarStyle.MOSAIC to avatarData.removePrefix("mosaic:")
        avatarData.startsWith("dots:") -> AvatarStyle.DOTS to avatarData.removePrefix("dots:")
        avatarData.startsWith("stripes:") -> AvatarStyle.STRIPES to avatarData.removePrefix("stripes:")
        avatarData.startsWith("diamond:") -> AvatarStyle.DIAMOND to avatarData.removePrefix("diamond:")
        else -> AvatarStyle.SOLID to avatarData
    }
}

/**
 * 生成头像数据字符串
 */
private fun generateAvatarData(style: AvatarStyle, colorData: String): String {
    return "${style.prefix}$colorData"
}

/**
 * 带样式的头像显示组件
 */
@Composable
fun StyledAvatar(
    avatarData: String,
    size: Dp,
    initial: String,
    modifier: Modifier = Modifier
) {
    val (style, colorData) = parseAvatarData(avatarData)
    
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            AvatarStyle.SOLID -> {
                val color = try { Color(colorData.toColorInt()) } catch (e: Exception) { Color.Gray }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                )
            }
            AvatarStyle.GRADIENT -> {
                val colors = colorData.split(":").mapNotNull { 
                    try { Color(it.toColorInt()) } catch (e: Exception) { null }
                }
                if (colors.size >= 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(colors))
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
                }
            }
            AvatarStyle.MOSAIC -> {
                val baseColor = try { Color(colorData.toColorInt()) } catch (e: Exception) { Color.Gray }
                MosaicBackground(baseColor = baseColor, modifier = Modifier.fillMaxSize())
            }
            AvatarStyle.DOTS -> {
                val baseColor = try { Color(colorData.toColorInt()) } catch (e: Exception) { Color.Gray }
                DotsBackground(baseColor = baseColor, modifier = Modifier.fillMaxSize())
            }
            AvatarStyle.STRIPES -> {
                val baseColor = try { Color(colorData.toColorInt()) } catch (e: Exception) { Color.Gray }
                StripesBackground(baseColor = baseColor, modifier = Modifier.fillMaxSize())
            }
            AvatarStyle.DIAMOND -> {
                val baseColor = try { Color(colorData.toColorInt()) } catch (e: Exception) { Color.Gray }
                DiamondBackground(baseColor = baseColor, modifier = Modifier.fillMaxSize())
            }
        }
        
        // 显示首字母
        Text(
            text = initial,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = (size.value * 0.4f).sp),
            color = Color.White
        )
    }
}

/**
 * 马赛克背景
 */
@Composable
private fun MosaicBackground(baseColor: Color, modifier: Modifier = Modifier) {
    val seed = remember { Random.nextInt() }
    Canvas(modifier = modifier) {
        val cellSize = size.minDimension / 6
        val rows = (size.height / cellSize).toInt() + 1
        val cols = (size.width / cellSize).toInt() + 1
        val random = Random(seed)
        
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val variation = random.nextFloat() * 0.4f - 0.2f
                val color = baseColor.copy(
                    red = (baseColor.red + variation).coerceIn(0f, 1f),
                    green = (baseColor.green + variation).coerceIn(0f, 1f),
                    blue = (baseColor.blue + variation).coerceIn(0f, 1f)
                )
                drawRect(
                    color = color,
                    topLeft = Offset(col * cellSize, row * cellSize),
                    size = Size(cellSize, cellSize)
                )
            }
        }
    }
}

/**
 * 波点背景
 */
@Composable
private fun DotsBackground(baseColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // 背景色（稍暗）
        drawRect(color = baseColor.copy(alpha = 0.8f))
        
        val dotRadius = size.minDimension / 12
        val spacing = dotRadius * 2.5f
        val rows = (size.height / spacing).toInt() + 2
        val cols = (size.width / spacing).toInt() + 2
        
        // 绘制波点
        val lighterColor = baseColor.copy(
            red = (baseColor.red + 0.3f).coerceIn(0f, 1f),
            green = (baseColor.green + 0.3f).coerceIn(0f, 1f),
            blue = (baseColor.blue + 0.3f).coerceIn(0f, 1f)
        )
        
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val offsetX = if (row % 2 == 0) 0f else spacing / 2
                drawCircle(
                    color = lighterColor,
                    radius = dotRadius,
                    center = Offset(col * spacing + offsetX, row * spacing)
                )
            }
        }
    }
}

/**
 * 条纹背景
 */
@Composable
private fun StripesBackground(baseColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stripeWidth = size.minDimension / 8
        val numStripes = ((size.width + size.height) / stripeWidth).toInt() + 2
        
        val lighterColor = baseColor.copy(
            red = (baseColor.red + 0.2f).coerceIn(0f, 1f),
            green = (baseColor.green + 0.2f).coerceIn(0f, 1f),
            blue = (baseColor.blue + 0.2f).coerceIn(0f, 1f)
        )
        
        // 背景
        drawRect(color = baseColor)
        
        // 斜条纹
        for (i in 0 until numStripes step 2) {
            val start = i * stripeWidth - size.height
            drawLine(
                color = lighterColor,
                start = Offset(start, size.height),
                end = Offset(start + size.height, 0f),
                strokeWidth = stripeWidth
            )
        }
    }
}

/**
 * 菱形背景
 */
@Composable
private fun DiamondBackground(baseColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cellSize = size.minDimension / 4
        val rows = (size.height / cellSize).toInt() + 2
        val cols = (size.width / cellSize).toInt() + 2
        
        val lighterColor = baseColor.copy(
            red = (baseColor.red + 0.15f).coerceIn(0f, 1f),
            green = (baseColor.green + 0.15f).coerceIn(0f, 1f),
            blue = (baseColor.blue + 0.15f).coerceIn(0f, 1f)
        )
        val darkerColor = baseColor.copy(
            red = (baseColor.red - 0.15f).coerceIn(0f, 1f),
            green = (baseColor.green - 0.15f).coerceIn(0f, 1f),
            blue = (baseColor.blue - 0.15f).coerceIn(0f, 1f)
        )
        
        // 背景
        drawRect(color = baseColor)
        
        // 菱形图案
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val offsetX = if (row % 2 == 0) 0f else cellSize / 2
                val centerX = col * cellSize + offsetX
                val centerY = row * cellSize
                val color = if ((row + col) % 2 == 0) lighterColor else darkerColor
                
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(centerX, centerY - cellSize / 2)
                    lineTo(centerX + cellSize / 2, centerY)
                    lineTo(centerX, centerY + cellSize / 2)
                    lineTo(centerX - cellSize / 2, centerY)
                    close()
                }
                drawPath(path, color)
            }
        }
    }
}

// ==================== 头像选择对话框 ====================

/**
 * 头像风格选择对话框
 */
@Composable
private fun AvatarStyleDialog(
    currentAvatarData: String,
    userInitial: String,
    onDismiss: () -> Unit,
    onAvatarSelected: (String) -> Unit
) {
    val (currentStyle, currentColorData) = parseAvatarData(currentAvatarData)
    var selectedStyle by remember { mutableStateOf(currentStyle) }
    var selectedColor by remember { mutableStateOf(
        if (currentStyle == AvatarStyle.GRADIENT) "#FF5722" else currentColorData
    ) }
    var selectedGradient by remember { mutableStateOf(
        if (currentStyle == AvatarStyle.GRADIENT) {
            currentColorData.split(":").let { if (it.size >= 2) it else listOf("#FF512F", "#DD2476") }
        } else gradientPresets[0]
    ) }
    
    val previewData = remember(selectedStyle, selectedColor, selectedGradient) {
        when (selectedStyle) {
            AvatarStyle.GRADIENT -> generateAvatarData(selectedStyle, selectedGradient.joinToString(":"))
            else -> generateAvatarData(selectedStyle, selectedColor)
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择头像样式") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 预览
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    StyledAvatar(
                        avatarData = previewData,
                        size = 100.dp,
                        initial = userInitial
                    )
                }
                
                // 风格选择
                Text("选择风格", style = MaterialTheme.typography.titleSmall)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AvatarStyle.entries) { style ->
                        FilterChip(
                            selected = selectedStyle == style,
                            onClick = { selectedStyle = style },
                            label = { Text(style.displayName, fontSize = 12.sp) }
                        )
                    }
                }
                
                // 根据风格显示不同的选择器
                if (selectedStyle == AvatarStyle.GRADIENT) {
                    Text("选择渐变", style = MaterialTheme.typography.titleSmall)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.height(180.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(gradientPresets) { gradient ->
                            val isSelected = selectedGradient == gradient
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            gradient.map { Color(it.toColorInt()) }
                                        )
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { selectedGradient = gradient },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "已选择",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text("选择颜色", style = MaterialTheme.typography.titleSmall)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        modifier = Modifier.height(200.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(avatarColorOptions) { color ->
                            val isSelected = selectedColor == color
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(color.toColorInt()))
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { selectedColor = color },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "已选择",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAvatarSelected(previewData) },
                enabled = previewData != currentAvatarData
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ==================== 其他对话框 ====================

/**
 * 修改密码对话框
 */
@Composable
private fun ChangePasswordDialog(
    email: String,
    authViewModel: AuthViewModel,
    authState: AuthViewModel.AuthState,
    onDismiss: () -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var oldPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("旧密码") },
                    visualTransformation = if (oldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { oldPasswordVisible = !oldPasswordVisible }) {
                            Icon(
                                imageVector = if (oldPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "切换密码可见性"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("新密码") },
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "切换密码可见性"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("确认新密码") },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "切换密码可见性"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    authViewModel.updatePassword(
                        email = email,
                        oldPassword = oldPassword,
                        newPassword = newPassword,
                        confirmPassword = confirmPassword
                    )
                },
                enabled = authState !is AuthViewModel.AuthState.Loading &&
                        oldPassword.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank()
            ) {
                Text(if (authState is AuthViewModel.AuthState.Loading) "处理中..." else "确认修改")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 设置私密问题对话框
 */
@Composable
private fun SecurityQuestionDialog(
    email: String,
    currentQuestion: String,
    authViewModel: AuthViewModel,
    authState: AuthViewModel.AuthState,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var question by remember { mutableStateOf(currentQuestion) }
    var answer by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置私密问题") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("当前密码") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "切换密码可见性"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("私密问题") },
                    placeholder = { Text("例如：我最喜欢的颜色是？") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("答案") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    authViewModel.updateSecurityQuestion(
                        email = email,
                        password = password,
                        question = question,
                        answer = answer
                    )
                },
                enabled = authState !is AuthViewModel.AuthState.Loading &&
                        password.isNotBlank() && question.isNotBlank() && answer.isNotBlank()
            ) {
                Text(if (authState is AuthViewModel.AuthState.Loading) "处理中..." else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun InfoCard(label: String, value: String, trailingIcon: @Composable (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(text = value, style = MaterialTheme.typography.bodyLarge, fontSize = 18.sp)
            }
            trailingIcon?.invoke()
        }
    }
}

@Composable
private fun LogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("退出登录") },
        text = { Text("您确定要退出登录吗？") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EmailCodeDialog(
    email: String,
    purpose: AuthViewModel.VerificationPurpose,
    code: String,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onResend: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean,
    codeCountdown: Int
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("邮箱验证码验证") },
        text = {
            Column {
                Text(text = "为了完成敏感操作，需校验发送到 $email 的验证码。")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text("验证码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "操作类型：${purpose.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = code.isNotBlank() && !isLoading) {
                Text(if (isLoading) "校验中…" else "提交")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onResend, enabled = !isLoading && codeCountdown == 0) {
                    Text(if (codeCountdown == 0) "重发" else "重发(${codeCountdown}s)")
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
