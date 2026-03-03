package ru.merrcurys.seacard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Системное окно выбора: «Камера» или «Галерея».
 * Использует Intent.ACTION_CHOOSER с EXTRA_INTENT (галерея) и EXTRA_INITIAL_INTENTS (камера).
 * Возвращает интент и Uri снимка камеры — если пользователь выберет камеру, результат приходит по этому Uri (result.data?.data часто null).
 */
fun createImagePickerChooserIntent(context: Context): Pair<Intent, Uri?> {
    val photoFile = File(context.cacheDir, "cover_${System.currentTimeMillis()}.jpg")
    val cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
    val galleryIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "image/*"
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
        putExtra(Intent.EXTRA_INTENT, galleryIntent)
        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
        putExtra(Intent.EXTRA_TITLE, "Камера или галерея")
    }
    return Pair(chooserIntent, cameraUri)
}
