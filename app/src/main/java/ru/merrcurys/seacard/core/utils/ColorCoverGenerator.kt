package ru.merrcurys.seacard.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as GColor
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream

/**
 * Генерирует и сохраняет обложку карты из цвета и названия.
 */
object ColorCoverGenerator {

    private fun isColorDark(color: Int): Boolean {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        val brightness = (red * 299 + green * 587 + blue * 114) / 1000
        return brightness < 128
    }

    /**
     * Генерирует Bitmap обложки из цвета фона и названия карты.
     */
    fun generateColorCoverBitmap(name: String, color: Int): Bitmap? = try {
        val aspectRatio = 1.574f
        val width = 600
        val height = (width / aspectRatio).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val textColor = if (isColorDark(color)) GColor.WHITE else GColor.BLACK
        val paint = Paint().apply {
            setColor(textColor)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val x = width / 2f
        val y = height / 2f - (paint.descent() + paint.ascent()) / 2f
        val displayName = if (name.isBlank()) "Карта" else name
        canvas.drawText(displayName, x, y, paint)
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /**
     * Генерирует обложку и сохраняет в WebP. Возвращает путь к файлу или null.
     */
    fun generateAndSaveAsWebp(context: Context, name: String, color: Int, fileName: String): String? {
        val bitmap = generateColorCoverBitmap(name, color) ?: return null
        try {
            val coversDir = File(context.filesDir, "covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val file = File(coversDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
            }
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            bitmap.recycle()
        }
    }
}
