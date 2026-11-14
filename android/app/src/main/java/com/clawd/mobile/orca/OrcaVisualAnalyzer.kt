package com.clawd.mobile.orca

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Bundled ML Kit models; images are never persisted or transmitted by ORCA. */
object OrcaVisualAnalyzer {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }

    /** Owns [bitmap] until both offline ML tasks complete. */
    fun analyze(bitmap: Bitmap, finished: (String) -> Unit) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val text = recognizer.process(image)
            val labels = labeler.process(image)
            Tasks.whenAllComplete(text, labels).addOnCompleteListener {
                val description = try {
                    val ocr = if (text.isSuccessful) text.result?.text.orEmpty().replace('\n', ' ').take(900) else ""
                    val objects = if (labels.isSuccessful)
                        labels.result.orEmpty().filter { it.confidence >= 0.65f }.take(6).joinToString(", ") { it.text }
                    else ""
                    if (ocr.isBlank() && objects.isBlank()) "" else "Visible text: $ocr. Image labels: $objects"
                } catch (_: Exception) { "" }
                bitmap.recycle()
                finished(description)
            }
        } catch (_: Exception) {
            bitmap.recycle()
            finished("")
        }
    }
}
