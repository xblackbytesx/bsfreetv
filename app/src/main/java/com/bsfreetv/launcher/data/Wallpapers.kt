package com.bsfreetv.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import com.bsfreetv.launcher.domain.palette.LauncherPalette
import com.bsfreetv.launcher.domain.palette.PaletteBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

@Immutable
data class Wallpaper(val id: String, val title: String, val credit: String?, val isUser: Boolean)

@Serializable
internal data class WallpaperIndex(val wallpapers: List<Entry>) {
    @Serializable
    data class Entry(val id: String, val title: String, val credit: String? = null, val license: String? = null)
}

/**
 * Built-in wallpapers (assets/wallpapers, 2752x1548 WebP from tools/import_wallpapers.py)
 * plus the user's own in `Android/data/<pkg>/files/wallpapers/`: anything put
 * there (adb push, or the in-app import) shows up in the picker.
 */
@Singleton
class Wallpapers @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val palettes = HashMap<String, LauncherPalette>()

    private val builtIns: List<Wallpaper> by lazy {
        runCatching {
            context.assets.open("$ASSET_DIR/index.json").use { json.decodeFromString(WallpaperIndex.serializer(), it.reader().readText()) }
                .wallpapers.map { Wallpaper(it.id, it.title, it.credit, isUser = false) }
        }.onFailure { Log.e(TAG, "wallpaper index unreadable", it) }.getOrDefault(emptyList())
    }

    /** Null when external storage is unavailable; user wallpapers are then simply absent. */
    val userDir: File? get() = context.getExternalFilesDir(USER_DIR)?.also { it.mkdirs() }

    suspend fun all(): List<Wallpaper> = withContext(Dispatchers.IO) {
        val user = userDir?.listFiles { f -> f.isFile && f.extension.lowercase() in IMAGE_EXT }
            ?.sortedByDescending { it.lastModified() }
            ?.map { Wallpaper("$USER_PREFIX${it.name}", it.nameWithoutExtension.replace('-', ' ').replace('_', ' '), null, isUser = true) }
            .orEmpty()
        user + builtIns
    }

    private fun userFile(id: String): File? = userDir?.resolve(id.removePrefix(USER_PREFIX))?.takeIf { it.isFile }

    private fun open(id: String, thumb: Boolean): InputStream? = runCatching {
        if (id.startsWith(USER_PREFIX)) userFile(id)?.inputStream()
        else context.assets.open(if (thumb) "$ASSET_DIR/thumbs/$id.webp" else "$ASSET_DIR/$id.webp")
    }.getOrNull()

    /**
     * The size a full wallpaper is decoded at: the display's own pixels (the
     * Shield renders at its output resolution, 3840x2160 on a 4K TV), capped
     * at 4K. Never larger than the image itself; see [decodeCropped].
     */
    val screenSize: Pair<Int, Int>
        get() {
            val m = context.resources.displayMetrics
            val w = maxOf(m.widthPixels, m.heightPixels).coerceIn(MIN_SCREEN_W, MAX_SCREEN_W)
            return w to w * 9 / 16
        }

    /**
     * Screen-sized bitmap, centre-cropped to 16:9. [hardware] keeps it in GPU
     * memory only (no per-frame upload, no Java heap); off for tests.
     */
    suspend fun loadFull(id: String, hardware: Boolean = true): Bitmap? = withContext(Dispatchers.IO) {
        val (w, h) = screenSize
        runCatching { decodeCropped(id, thumb = false, w, h)?.let { if (hardware) it.toHardware() else it } }
            .onFailure { Log.w(TAG, "cannot load $id", it) }.getOrNull()
    }

    suspend fun loadThumb(id: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { decodeCropped(id, thumb = true, THUMB_W, THUMB_H) }.getOrNull()
    }

    /** Palette from a ~120 px sample, as Impasto does. Cached per wallpaper. */
    suspend fun palette(id: String): LauncherPalette = palettes[id] ?: withContext(Dispatchers.Default) {
        val sample = runCatching { decodeCropped(id, thumb = true, SAMPLE_W, SAMPLE_H) }.getOrNull()
        val palette = sample?.let {
            val px = IntArray(it.width * it.height)
            it.getPixels(px, 0, it.width, 0, 0, it.width, it.height)
            it.recycle()
            PaletteBuilder.fromPixels(px)
        } ?: LauncherPalette.Neutral
        palette.also { palettes[id] = it }
    }

    /** Copy an image the user picked into the wallpaper folder as a screen-sized WebP. Returns its id. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = userDir ?: return@runCatching null
            val (w, h) = screenSize
            val bmp = decodeCropped({ context.contentResolver.openInputStream(uri) }, w, h) ?: return@runCatching null
            val name = "imported-${System.currentTimeMillis()}.webp"
            dir.resolve(name).outputStream().use { bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 88, it) }
            bmp.recycle()
            "$USER_PREFIX$name"
        }.onFailure { Log.w(TAG, "import failed", it) }.getOrNull()
    }

    private fun decodeCropped(id: String, thumb: Boolean, w: Int, h: Int): Bitmap? =
        decodeCropped({ open(id, thumb) }, w, h)

    /**
     * Centre-crop to the aspect of [maxW] x [maxH] and scale down to that size.
     * Never scales up: an image smaller than the target comes out at its own
     * (cropped) size and the GPU stretches it at draw time, which costs no memory.
     */
    private fun decodeCropped(source: () -> InputStream?, maxW: Int, maxH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null; success is the filled-in size.
        (source() ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val aspect = maxW.toFloat() / maxH
        val cropW = minOf(bounds.outWidth, (bounds.outHeight * aspect).toInt())
        val w = minOf(maxW, cropW)
        val h = (w / aspect).toInt()
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val decoded = source()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: return null
        if (decoded.width == w && decoded.height == h) return decoded
        // Centre-crop to the target aspect, then scale with filtering.
        val target = w.toFloat() / h
        val src = if (decoded.width.toFloat() / decoded.height > target) {
            val cw = (decoded.height * target).toInt()
            Rect((decoded.width - cw) / 2, 0, (decoded.width + cw) / 2, decoded.height)
        } else {
            val ch = (decoded.width / target).toInt()
            Rect(0, (decoded.height - ch) / 2, decoded.width, (decoded.height + ch) / 2)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(decoded, src, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        decoded.recycle()
        return out
    }

    private fun Bitmap.toHardware(): Bitmap {
        val hw = runCatching { copy(Bitmap.Config.HARDWARE, false) }.getOrNull() ?: return this
        recycle()
        return hw
    }

    companion object {
        const val USER_PREFIX = "user:"
        private const val TAG = "Wallpapers"
        private const val ASSET_DIR = "wallpapers"
        private const val USER_DIR = "wallpapers"
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
        private const val MIN_SCREEN_W = 1280
        private const val MAX_SCREEN_W = 3840
        const val THUMB_W = 384
        const val THUMB_H = 216
        private const val SAMPLE_W = 120
        private const val SAMPLE_H = 68
    }
}
