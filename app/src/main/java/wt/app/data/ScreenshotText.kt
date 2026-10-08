package wt.app.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import wt.core.io.OcrLine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Recognises the text in an image on the phone (ML Kit, bundled model, nothing is uploaded).
 * The Japanese model also reads Latin text, so one recogniser covers Strava and the body-scale app.
 */
suspend fun recognizeLines(context: Context, uri: Uri): List<OcrLine> {
    val image = InputImage.fromFilePath(context, uri)
    val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    try {
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            OcrLine(line.text, box.left, box.top, box.right, box.bottom)
        }
    } finally {
        recognizer.close()
    }
}
