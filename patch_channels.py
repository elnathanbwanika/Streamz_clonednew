import sys

with open("app/src/main/java/com/linkstreamz/app/ChannelsScreen.kt", "r") as f:
    content = f.read()

old_badge = """        // Initials badge top-center
        Box(
            Modifier.align(Alignment.TopCenter)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(channel.initials, color = Color.White, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }"""

new_badge = """        // Logo or Initials badge top-center
        val logoUrl = channel.logo
        val isValidUrl = !logoUrl.isNullOrBlank() && (logoUrl.startsWith("http://") || logoUrl.startsWith("https://"))

        Box(
            Modifier.align(Alignment.TopCenter)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            if (isValidUrl) {
                coil.compose.SubcomposeAsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = channel.title,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                ) {
                    when (painter.state) {
                        is coil.compose.AsyncImagePainter.State.Loading,
                        is coil.compose.AsyncImagePainter.State.Error -> {
                            Text(channel.initials, color = Color.White, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        is coil.compose.AsyncImagePainter.State.Success -> {
                            coil.compose.SubcomposeAsyncImageContent()
                        }
                        else -> {
                            Text(channel.initials, color = Color.White, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                Text(channel.initials, color = Color.White, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }"""

if old_badge in content:
    content = content.replace(old_badge, new_badge)
else:
    print("Could not find old_badge in ChannelsScreen.kt")
    sys.exit(1)

with open("app/src/main/java/com/linkstreamz/app/ChannelsScreen.kt", "w") as f:
    f.write(content)
print("Updated ChannelsScreen.kt")
