package wt.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The share-menu target for the partner card: the PNG plus the caption as the message text. */
fun shareIntent(image: Uri, caption: String): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "image/png"
    putExtra(Intent.EXTRA_STREAM, image)
    putExtra(Intent.EXTRA_TEXT, caption)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/** Writes [bitmap] to cache/share/[name].png (replacing the last one) and returns its FileProvider URI. */
suspend fun saveSharePng(context: Context, bitmap: ImageBitmap, name: String): Uri = withContext(Dispatchers.IO) {
    val file = File(File(context.cacheDir, "share").apply { mkdirs() }, "$name.png")
    file.outputStream().use { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    FileProvider.getUriForFile(context, "${context.packageName}.share", file)
}

/** Opens Android's share menu (WhatsApp, LINE, …) with the card and its caption. */
fun Context.startShare(image: Uri, caption: String) {
    startActivity(Intent.createChooser(shareIntent(image, caption), "Share progress"))
}
