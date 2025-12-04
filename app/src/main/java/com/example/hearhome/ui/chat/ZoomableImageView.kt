package com.example.hearhome.ui.chat

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
fun ZoomableImageView(
    model: Any,
    modifier: Modifier = Modifier
) {
    val scale = remember { mutableStateOf(1f) }
    val offset = remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale.value * zoom).coerceIn(1f, 4f)

                    if (newScale > 1f) {
                        val newOffset = offset.value + pan

                        val imageWidth = size.width * newScale
                        val imageHeight = size.height * newScale

                        val horizontalPadding = (imageWidth - size.width) / 2
                        val verticalPadding = (imageHeight - size.height) / 2

                        offset.value = Offset(
                            x = newOffset.x.coerceIn(-horizontalPadding, horizontalPadding),
                            y = newOffset.y.coerceIn(-verticalPadding, verticalPadding)
                        )
                        scale.value = newScale
                    } else {
                        scale.value = 1f
                        offset.value = Offset.Zero
                    }
                }
            }
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Zoomable image",
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.value.x
                    translationY = offset.value.y
                },
            contentScale = ContentScale.Fit
        )
    }
}
