package com.example.hearhome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import kotlin.random.Random

/**
 * Shared avatar renderer that understands solid, gradient, mosaic, dots, stripes, and diamond data strings.
 */
enum class AvatarStyle(val displayName: String, val prefix: String) {
    SOLID("\u7eaf\u8272", ""),
    GRADIENT("\u6e10\u53d8", "gradient:"),
    MOSAIC("\u9a6c\u8d5b\u514b", "mosaic:"),
    DOTS("\u6ce2\u70b9", "dots:"),
    STRIPES("\u6761\u7eb9", "stripes:"),
    DIAMOND("\u83f1\u5f62", "diamond:")
}

fun parseAvatarData(avatarData: String): Pair<AvatarStyle, String> {
    val normalized = avatarData.trim().ifBlank { "#CCCCCC" }
    return when {
        normalized.startsWith("gradient:") -> AvatarStyle.GRADIENT to normalized.removePrefix("gradient:")
        normalized.startsWith("mosaic:") -> AvatarStyle.MOSAIC to normalized.removePrefix("mosaic:")
        normalized.startsWith("dots:") -> AvatarStyle.DOTS to normalized.removePrefix("dots:")
        normalized.startsWith("stripes:") -> AvatarStyle.STRIPES to normalized.removePrefix("stripes:")
        normalized.startsWith("diamond:") -> AvatarStyle.DIAMOND to normalized.removePrefix("diamond:")
        else -> AvatarStyle.SOLID to normalized
    }
}

fun generateAvatarData(style: AvatarStyle, colorData: String): String = "${style.prefix}$colorData"

@Composable
fun StyledAvatar(
    avatarData: String,
    size: Dp,
    initial: String?,
    modifier: Modifier = Modifier,
    showInitial: Boolean = true
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
                val color = runCatching { Color(colorData.toColorInt()) }.getOrElse { Color.Gray }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color)
                )
            }
            AvatarStyle.GRADIENT -> {
                val colors = colorData.split(":").mapNotNull { runCatching { Color(it.toColorInt()) }.getOrNull() }
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
                val baseColor = runCatching { Color(colorData.toColorInt()) }.getOrElse { Color.Gray }
                MosaicBackground(baseColor = baseColor, modifier = Modifier.fillMaxSize())
            }
            AvatarStyle.DOTS -> {
                val baseColor = runCatching { Color(colorData.toColorInt()) }.getOrElse { Color.Gray }
                DotsBackground(baseColor = baseColor, modifier = Modifier.fillMaxSize())
            }
            AvatarStyle.STRIPES -> {
                val baseColor = runCatching { Color(colorData.toColorInt()) }.getOrElse { Color.Gray }
                StripesBackground(baseColor = baseColor, modifier = Modifier.fillMaxSize())
            }
            AvatarStyle.DIAMOND -> {
                val baseColor = runCatching { Color(colorData.toColorInt()) }.getOrElse { Color.Gray }
                DiamondBackground(baseColor = baseColor, modifier = Modifier.fillMaxSize())
            }
        }

        if (showInitial && !initial.isNullOrBlank()) {
            Text(
                text = initial.take(1),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = (size.value * 0.4f).sp),
                color = Color.White
            )
        }
    }
}

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

@Composable
private fun DotsBackground(baseColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(color = baseColor.copy(alpha = 0.8f))

        val dotRadius = size.minDimension / 12
        val spacing = dotRadius * 2.5f
        val rows = (size.height / spacing).toInt() + 2
        val cols = (size.width / spacing).toInt() + 2

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

        drawRect(color = baseColor)

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

        drawRect(color = baseColor)

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
