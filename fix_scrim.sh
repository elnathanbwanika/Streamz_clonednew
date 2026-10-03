sed -i '561,572c\
            androidx.compose.animation.AnimatedVisibility(\
                visible = controlsVisible,\
                enter = fadeIn(tween(300)),\
                exit = fadeOut(tween(600)),\
                modifier = Modifier.matchParentSize(),\
            ) {\
                Box(\
                    Modifier.fillMaxSize()\
                        .background(\
                            Brush.verticalGradient(\
                                0.00f to Color.Black.copy(alpha = 0.28f),\
                                0.32f to Color.Transparent,\
                                0.55f to Color.Transparent,\
                                1.00f to Color.Black.copy(alpha = 0.55f)\
                            )\
                        )\
                )\
            }' app/src/main/java/com/linkstreamz/app/PlayerScreen.kt
