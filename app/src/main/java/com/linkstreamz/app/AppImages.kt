[Earlier content for app/src/main/java/com/linkstreamz/app/AppImages.kt (1140 chars, already written to disk). The module surface below may not show every signature you need — re-read with read_file before editing this file again, OR before importing/calling anything from it in another file. Do not recreate it from scratch and do not guess a function's parameters from memory.
Module surface:
import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * One image loader for the whole app.
 *
 * Previously the global loader (tunnel-aware) and [direct] (a second loader
 * with its own empty memory cache) coexisted. The same thumbnail requested on
 * two screens was fetched twice, and a transient failure on one had no cached
 * copy to fall back to. Everything now shares this instance so a thumbnail
 * warmed on one screen is instantly available on every other.
 */
object AppImages {

    @Volatile
    private var shared: ImageLoader? = null

    private val retry = Interceptor { chain ->
        var lastError: IOException? = null
        repeat(IMAGE_ATTEMPTS) { attempt ->
            try {
                val response: Response = chain.proceed(chain.request())
                return@Interceptor response
            } catch (e: IOException) {
                lastError = e
                if (attempt < IMAGE_ATTEMPTS - 1) {
                    Thread.sleep(120L * (attempt + 1))
                }
            }
        }
        throw lastError ?: IOException("Image request failed")
    }

    fun loader(context: Context): ImageLoader =
        shared ?: synchronized(this) {
            shared ?: ImageLoader.Builder(context.applicationContext)
                .okHttpClient(
                    HttpClients.newBuilder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(12, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .addInterceptor(retry)
                        .build()
                )
                .crossfade(160)
                .build()
                .also { shared = it }
        }

    /** Kept for existing call sites; now the same warmed loader as everything else. */
    fun direct(context: Context): ImageLoader = loader(context)

    /**
     * Warm the memory cache with the first [max] thumbnails as soon as a feed
     * arrives. Never throws and never blocks the caller: a failed prefetch just
     * leaves the image to load normally when its card is composed.
     */
    suspend fun prefetch(context: Context, urls: List<String>, max: Int = 24) {
        withContext(Dispatchers.IO) {
            val loader = loader(context)
            urls.asSequence()
                .filter { it.startsWith("https://") || it.startsWith("http://") }
                .distinct()
                .take(max)
                .forEach { url ->
                    runCatching {
                        loader.enqueue(
                            ImageRequest.Builder(context.applicationContext)
                                .data(url)
                                .build()
                        )
                    }
                }
        }
    }
}

private const val IMAGE_ATTEMPTS = 3