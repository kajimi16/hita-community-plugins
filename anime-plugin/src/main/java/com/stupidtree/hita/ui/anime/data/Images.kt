package com.stupidtree.hita.ui.anime.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 封面加载器。
 *
 * 宿主的图片库不是插件公开契约，因此这里自带一个最小的两级缓存：
 * 内存 LRU（按字节计）+ 磁盘缓存（cacheDir/covers）。
 * 下载在 IO 线程完成，Compose 侧只读缓存。
 */
object CoverLoader {

    private val memory = object : LruCache<String, Bitmap>(maxMemoryBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Volatile
    private var cacheDir: File? = null

    /** 自建缓存服务器（校内/国内）。封面先走它们的 Next Image，再回退直连。 */
    @Volatile
    private var proxyBases: List<String> = emptyList()

    @Volatile
    private var proxyFailures: Int = 0

    @Volatile
    private var proxyBlockedUntil: Long = 0L

    fun configure(bases: List<String>) {
        val normalized = bases.map { it.trim().trimEnd('/') }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
        if (normalized == proxyBases) return
        proxyBases = normalized
        proxyFailures = 0
        proxyBlockedUntil = 0L
    }

    fun init(context: Context) {
        if (cacheDir != null) return
        cacheDir = File(context.applicationContext.cacheDir, "anime-covers").apply { mkdirs() }
    }

    /** 清空内存与磁盘缓存（设置页的「清理缓存」）。 */
    fun clear(context: Context) {
        memory.evictAll()
        val dir = File(context.applicationContext.cacheDir, "anime-covers")
        dir.deleteRecursively()
        dir.mkdirs()
        cacheDir = dir
    }

    suspend fun load(url: String?, maxWidth: Int = 720): Bitmap? {
        val key = url?.asHttps()?.takeIf { it.isNotBlank() } ?: return null
        val bucket = bucketOf(maxWidth)
        val memoryKey = "$key@$bucket"
        memory.get(memoryKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            memory.get(memoryKey)?.let { return@withContext it }
            val bitmap = readFromDisk(key, bucket) ?: downloadWithFallback(key, bucket)?.also {
                writeToDisk(key, bucket, it)
            }
            if (bitmap != null) memory.put(memoryKey, bitmap)
            bitmap
        }
    }

    private fun downloadWithFallback(url: String, bucket: Int): Bitmap? {
        val now = System.currentTimeMillis()
        if (proxyBases.isNotEmpty() && now >= proxyBlockedUntil) {
            val encoded = URLEncoder.encode(url, "UTF-8").replace("+", "%20")
            for (base in proxyBases) {
                val proxied = "$base/_next/image?url=$encoded&w=${nextWidth(bucket)}&q=85"
                val bitmap = download(proxied, bucket, viaProxy = true)
                if (bitmap != null) {
                    proxyFailures = 0
                    return bitmap
                }
            }
            proxyFailures++
            // 两台服务器都不可用/返回非图片时，5 分钟内不再拖慢每张封面。
            if (proxyFailures >= 2) {
                proxyBlockedUntil = now + 5 * 60 * 1000L
            }
        }
        return download(url, bucket, viaProxy = false)
    }

    private fun download(url: String, maxWidth: Int, viaProxy: Boolean): Bitmap? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = if (viaProxy) 4_000 else 12_000
            conn.readTimeout = if (viaProxy) 10_000 else 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", BgmApi.USER_AGENT)
            conn.setRequestProperty("Referer", "https://bgm.tv/")
            if (viaProxy) {
                conn.setRequestProperty("Accept", "image/webp,image/*")
            }
            val status = conn.responseCode
            val type = conn.contentType
            val bytes = conn.inputStream.use { it.readBytes() }
            decode(bytes, maxWidth)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun readFromDisk(url: String, bucket: Int): Bitmap? {
        val file = diskFile(url, bucket) ?: return null
        if (!file.isFile) return null
        return runCatching { decode(file.readBytes(), bucket) }.getOrNull()
    }

    private fun writeToDisk(url: String, bucket: Int, bitmap: Bitmap) {
        val file = diskFile(url, bucket) ?: return
        runCatching {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
        }
    }

    private fun diskFile(url: String, bucket: Int): File? {
        val dir = cacheDir ?: return null
        val name = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
        return File(dir, "${name}_$bucket.jpg")
    }

    /** 缓存按尺寸分档，避免小图先入缓存后，大图读到低分辨率版本。 */
    private fun bucketOf(width: Int): Int = when {
        width <= 320 -> 320
        width <= 640 -> 640
        width <= 1080 -> 1080
        else -> 1600
    }

    /** Next Image 只接受固定的 `w` 档位。 */
    private fun nextWidth(bucket: Int): Int = when (bucket) {
        320 -> 384
        640 -> 640
        1080 -> 1080
        else -> 1920
    }

    private fun decode(bytes: ByteArray, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        // 保证解码后的宽度不低于目标，宁大勿小；否则大图会先糊一次再放大。
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun maxMemoryBytes(): Int {
        val max = Runtime.getRuntime().maxMemory()
        return (max / 8).coerceIn(8L * 1024 * 1024, 64L * 1024 * 1024).toInt()
    }
}
