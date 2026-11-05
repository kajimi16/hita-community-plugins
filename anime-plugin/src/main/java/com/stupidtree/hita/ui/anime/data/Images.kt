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
        memory.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            memory.get(key)?.let { return@withContext it }
            val bitmap = readFromDisk(key, maxWidth) ?: download(key, maxWidth)?.also {
                writeToDisk(key, it)
            }
            if (bitmap != null) memory.put(key, bitmap)
            bitmap
        }
    }

    private fun download(url: String, maxWidth: Int): Bitmap? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", BgmApi.USER_AGENT)
            conn.setRequestProperty("Referer", "https://bgm.tv/")
            val bytes = conn.inputStream.use { it.readBytes() }
            decode(bytes, maxWidth)
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun readFromDisk(url: String, maxWidth: Int): Bitmap? {
        val file = diskFile(url) ?: return null
        if (!file.isFile) return null
        return runCatching { decode(file.readBytes(), maxWidth) }.getOrNull()
    }

    private fun writeToDisk(url: String, bitmap: Bitmap) {
        val file = diskFile(url) ?: return
        runCatching {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
        }
    }

    private fun diskFile(url: String): File? {
        val dir = cacheDir ?: return null
        val name = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)
        return File(dir, "$name.jpg")
    }

    private fun decode(bytes: ByteArray, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxWidth * 2) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun maxMemoryBytes(): Int {
        val max = Runtime.getRuntime().maxMemory()
        return (max / 8).coerceIn(8L * 1024 * 1024, 64L * 1024 * 1024).toInt()
    }
}
