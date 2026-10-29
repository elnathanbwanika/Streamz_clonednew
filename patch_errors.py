import sys

with open("app/src/main/java/com/linkstreamz/app/PlayerScreen.kt", "r") as f:
    content = f.read()

old_code = """        if (nextIndex == -1 || fallbackIndex >= fallbackOrder.size - 1) {
            allServersFailed = true
            error = when (httpCode) {
                403 -> "Access denied (403). The stream may be geo-blocked."
                404 -> "Stream not found (404). The event may have ended."
                410 -> "Link expired (410). Please reload the app."
                in 500..599 -> "Server error ($httpCode). Try again later."
                else -> "All servers failed. Please try again later."
            }
            return
        }"""

new_code = """        if (nextIndex == -1 || fallbackIndex >= fallbackOrder.size - 1) {
            allServersFailed = true
            AppLogger.e("Engine", "All servers failed. Last HTTP code: $httpCode")
            error = when (httpCode) {
                403 -> "Stream not available in your location. Try either connecting VPN or using proxy in settings"
                404 -> "This event has ended"
                410 -> "Link expired (410). Please reload the app."
                in 500..599 -> "Server error ($httpCode). Try again later."
                else -> "All servers failed. Please try again later."
            }
            return
        }"""

if old_code in content:
    content = content.replace(old_code, new_code)
else:
    print("Could not find error messages to replace.")
    sys.exit(1)

with open("app/src/main/java/com/linkstreamz/app/PlayerScreen.kt", "w") as f:
    f.write(content)
print("Updated PlayerScreen.kt errors")
