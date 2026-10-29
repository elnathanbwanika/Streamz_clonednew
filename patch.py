import sys

with open("app/src/main/java/com/linkstreamz/app/PlayerScreen.kt", "r") as f:
    content = f.read()

# 1. Add LazyRow import
if "import androidx.compose.foundation.lazy.LazyRow" not in content:
    content = content.replace(
        "import androidx.compose.foundation.lazy.LazyColumn",
        "import androidx.compose.foundation.lazy.LazyColumn\nimport androidx.compose.foundation.lazy.LazyRow"
    )

# 2. Fix the call site
content = content.replace(
    "RelatedContent(repository, target, onTarget)",
    "RelatedContent(repository, moviesRepo, channelsRepo, target, onTarget)"
)

# 3. Add RelatedCardModel interface above RelatedContent
with open("/home/codespace/.gemini/antigravity-cli/brain/34752a80-2281-4adc-9117-7567a423202f/scratch/replace_related.kt", "r") as f:
    scratch_content = f.read()

# Replace the old RelatedContent and EmptyContentState
# Finding the start of RelatedContent docstring
old_related_start = content.find("/**\n * Related content section shown below the video player.\n */\n@Composable\nprivate fun RelatedContent")

# Finding the end of EmptyContentState
empty_content_end = content.find("}\n\n\nprivate fun enterFullscreen", old_related_start)
if empty_content_end == -1:
    print("Could not find end of EmptyContentState")
    sys.exit(1)

# we want to include the closing brace
empty_content_end = content.find("}", content.find("Text(message, color = LinkColors.MutedText, fontSize = 14.sp)", old_related_start)) + 1

# Define RelatedCardModel
related_card_model = """sealed interface RelatedCardModel {
    val key: String
    
    data class Event(
        val event: com.linkstreamz.app.Event,
        val nowMs: Long,
        val onClick: () -> Unit
    ) : RelatedCardModel {
        override val key = "event_${event.id}"
    }
    
    data class Highlight(
        val highlight: com.linkstreamz.app.Highlight,
        val onClick: () -> Unit
    ) : RelatedCardModel {
        override val key = "hl_${highlight.id}"
    }
    
    data class SingleItem(
        override val key: String,
        val categoryLine: String,
        val imageUrl: String?,
        val initials: String?,
        val name: String,
        val statusText: String?,
        val isLive: Boolean,
        val onClick: () -> Unit
    ) : RelatedCardModel
}

"""

new_empty_content = """
@Composable
internal fun EmptyContentState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = LinkColors.MutedText.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = LinkColors.MutedText, fontSize = 14.sp)
    }
}
"""

replacement = related_card_model + scratch_content + new_empty_content

content = content[:old_related_start] + replacement + content[empty_content_end:]

with open("app/src/main/java/com/linkstreamz/app/PlayerScreen.kt", "w") as f:
    f.write(content)

print("Patched PlayerScreen.kt successfully!")
