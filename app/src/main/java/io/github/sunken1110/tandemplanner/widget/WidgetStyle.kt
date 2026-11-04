package io.github.sunken1110.tandemplanner.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.edit
import androidx.core.graphics.scale
import java.io.File
import kotlin.math.max

/** What a widget's background is. */
enum class WidgetBackground {
    /** A light tint of the current season's color (the default). */
    SEASON,

    /** A see-through backdrop over the wallpaper; [WidgetStyle.level] sets how solid it is. */
    CLEAR,

    /** The user's own photo, darkened by [WidgetStyle.level] so the text stays readable. */
    PHOTO
}

/**
 * One placed widget's look. Each widget on the home screen has its own.
 *
 * @property level 0–80 (%): for [WidgetBackground.CLEAR] how solid the backdrop is,
 *           for [WidgetBackground.PHOTO] how dark the layer over the photo is.
 * @property photoStamp changes whenever a new photo is saved, so the widget
 *           reloads the picture instead of reusing the old one.
 * @property focusX where the photo is centered, as a fraction of its width
 *           (0 = left edge, 0.5 = middle, 1 = right edge).
 * @property focusY the same for its height.
 * @property zoom 1 (the photo just fills the widget) to [MAX_ZOOM].
 *
 * The focus point and [zoom] are kept apart from the widget's shape, so after
 * resizing, the photo is cut again for the new shape around the same spot
 * ([PhotoCrop]).
 */
data class WidgetStyle(
    val background: WidgetBackground = WidgetBackground.SEASON,
    val level: Int = DEFAULT_LEVEL,
    val photoStamp: Long = 0L,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val zoom: Float = 1f
) {
    /** Light text is needed: on a photo, or on a see-through backdrop that is still faint. */
    val lightText: Boolean
        get() = background == WidgetBackground.PHOTO ||
            (background == WidgetBackground.CLEAR && level < CLEAR_DARK_TEXT_FROM)

    companion object {
        const val DEFAULT_LEVEL = 40
        const val MAX_LEVEL = 80

        /** From this level up, a see-through backdrop is solid enough for normal (dark) text. */
        const val CLEAR_DARK_TEXT_FROM = 45

        const val MAX_ZOOM = 3f
    }
}

/**
 * Which part of a photo a widget shows: the "cover" rectangle of the widget's
 * shape (the largest one that fits inside the photo), made smaller by the zoom
 * and centered on the focus point, kept inside the photo.
 *
 * Used both by the widget (to cut the picture) and by the settings preview (to
 * show exactly the same part), so what the user sets is what the widget shows.
 */
object PhotoCrop {

    /** Source rectangle (left, top, width, height in photo pixels) for a widget of [aspect] = width / height. */
    fun rect(photoW: Int, photoH: Int, aspect: Float, focusX: Float, focusY: Float, zoom: Float): FloatArray {
        val a = aspect.coerceIn(0.2f, 5f)
        var w = photoW.toFloat()
        var h = photoH.toFloat()
        if (w / h > a) w = h * a else h = w / a
        val z = zoom.coerceIn(1f, WidgetStyle.MAX_ZOOM)
        w /= z
        h /= z
        val left = (focusX * photoW - w / 2).coerceIn(0f, photoW - w)
        val top = (focusY * photoH - h / 2).coerceIn(0f, photoH - h)
        return floatArrayOf(left, top, w, h)
    }

    /** The focus point after keeping the rectangle inside the photo (so dragging stops at the edges). */
    fun clampedFocus(photoW: Int, photoH: Int, aspect: Float, focusX: Float, focusY: Float, zoom: Float): Pair<Float, Float> {
        val r = rect(photoW, photoH, aspect, focusX, focusY, zoom)
        return (r[0] + r[2] / 2) / photoW to (r[1] + r[3] / 2) / photoH
    }

    /** The part of [photo] for a widget of [aspect], at most [maxSide] px on its long side. */
    fun crop(photo: Bitmap, aspect: Float, style: WidgetStyle, maxSide: Int = 640): Bitmap {
        val r = rect(photo.width, photo.height, aspect, style.focusX, style.focusY, style.zoom)
        val cropped = Bitmap.createBitmap(
            photo,
            r[0].toInt(),
            r[1].toInt(),
            r[2].toInt().coerceIn(1, photo.width - r[0].toInt()),
            r[3].toInt().coerceIn(1, photo.height - r[1].toInt())
        )
        val side = max(cropped.width, cropped.height)
        if (side <= maxSide) return cropped
        val scale = maxSide.toFloat() / side
        return cropped.scale(
            (cropped.width * scale).toInt().coerceAtLeast(1),
            (cropped.height * scale).toInt().coerceAtLeast(1)
        )
    }
}

/**
 * Saves each widget's [WidgetStyle] (keyed by Android's widget id) and its photo.
 *
 * Photos are copied into the app's private storage, scaled down to at most
 * [MAX_PHOTO_SIDE] pixels (the original in the gallery may later be moved or
 * deleted). The widget then cuts out the part it shows ([PhotoCrop]), small
 * enough for Android's limit on widget images. Nothing here leaves the phone.
 */
object WidgetStyleStore {
    private const val PREFS = "widget_style"
    /** Kept larger than any widget needs, so zooming in stays sharp. */
    private const val MAX_PHOTO_SIDE = 1600

    fun load(context: Context, appWidgetId: Int): WidgetStyle {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val background = runCatching {
            WidgetBackground.valueOf(prefs.getString("bg_$appWidgetId", null) ?: return WidgetStyle())
        }.getOrDefault(WidgetBackground.SEASON)
        val style = WidgetStyle(
            background = background,
            level = prefs.getInt("level_$appWidgetId", WidgetStyle.DEFAULT_LEVEL).coerceIn(0, WidgetStyle.MAX_LEVEL),
            photoStamp = prefs.getLong("photo_$appWidgetId", 0L),
            focusX = prefs.getFloat("fx_$appWidgetId", 0.5f).coerceIn(0f, 1f),
            focusY = prefs.getFloat("fy_$appWidgetId", 0.5f).coerceIn(0f, 1f),
            zoom = prefs.getFloat("zoom_$appWidgetId", 1f).coerceIn(1f, WidgetStyle.MAX_ZOOM)
        )
        // A photo style without its file (e.g. cleared storage) falls back to the season color.
        return if (style.background == WidgetBackground.PHOTO && !photoFile(context, appWidgetId).exists()) {
            style.copy(background = WidgetBackground.SEASON)
        } else style
    }

    fun save(context: Context, appWidgetId: Int, style: WidgetStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("bg_$appWidgetId", style.background.name)
            putInt("level_$appWidgetId", style.level)
            putLong("photo_$appWidgetId", style.photoStamp)
            putFloat("fx_$appWidgetId", style.focusX)
            putFloat("fy_$appWidgetId", style.focusY)
            putFloat("zoom_$appWidgetId", style.zoom)
        }
    }

    /** Forgets a removed widget's style and deletes its photo. */
    fun delete(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove("bg_$appWidgetId")
            remove("level_$appWidgetId")
            remove("photo_$appWidgetId")
            remove("rendered_$appWidgetId")
            remove("fx_$appWidgetId")
            remove("fy_$appWidgetId")
            remove("zoom_$appWidgetId")
        }
        photoFile(context, appWidgetId).delete()
    }

    /** Called by the widget after drawing: the refresh stamp it has drawn. */
    fun markRendered(context: Context, appWidgetId: Int, stamp: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong("rendered_$appWidgetId", stamp)
        }
    }

    /** The last refresh stamp the widget has drawn (0 if none yet). */
    fun renderedStamp(context: Context, appWidgetId: Int): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("rendered_$appWidgetId", 0L)

    fun photoFile(context: Context, appWidgetId: Int): File =
        File(File(context.filesDir, "widget_bg").apply { mkdirs() }, "$appWidgetId.jpg")

    /**
     * Copies the picked photo for a widget, scaled down. Returns the new stamp
     * for [WidgetStyle.photoStamp], or null if the photo couldn't be read.
     */
    fun importPhoto(context: Context, appWidgetId: Int, uri: Uri): Long? = runCatching {
        val bitmap = decodeScaled(context, uri) ?: return null
        photoFile(context, appWidgetId).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        }
        System.currentTimeMillis()
    }.getOrNull()

    /**
     * A placed widget's actual shape (width / height, in portrait), from the
     * options Android keeps for it; null when not known yet.
     */
    fun widgetAspect(options: android.os.Bundle?): Float? {
        val w = options?.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val h = options?.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        return if (w > 0 && h > 0) w.toFloat() / h else null
    }

    /** The widget's saved photo, or null. */
    fun loadPhoto(context: Context, appWidgetId: Int): Bitmap? {
        val file = photoFile(context, appWidgetId)
        return if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null
    }

    /** Decodes a picked image at most [MAX_PHOTO_SIDE] px on its long side, upright. */
    fun decodeScaled(context: Context, uri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val side = max(info.size.width, info.size.height)
            if (side > MAX_PHOTO_SIDE) {
                val scale = MAX_PHOTO_SIDE.toFloat() / side
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            // A software bitmap, so it can be compressed and sent to the widget.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()
}
