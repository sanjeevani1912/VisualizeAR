package com.example.dhruvar.ui.ar.capture

import android.content.Intent
import android.net.Uri

/**
 * A picture that was written to the phone's gallery. [uri] is the MediaStore address of the file
 * (a "content://" address, not a file path); [fileName] is what the gallery shows.
 */
data class SavedImage(val uri: Uri, val fileName: String) {
    /** The message shown after saving, naming the file and the folder. */
    val message: String get() = savedMessage(fileName)
}

object SavedImageSupport {
    const val FOLDER = "Pictures/DhruvAR"
    const val MIME_TYPE = "image/jpeg"
}

/** Pure text, kept separate so it can be unit-tested without a phone. */
fun savedMessage(fileName: String): String = "Saved $fileName in ${SavedImageSupport.FOLDER}"

/**
 * An "open this picture" request for the phone's gallery or viewer. The read grant lets the other
 * app read this one picture only. Starting it can fail when no app can show JPEGs; the caller
 * must catch [android.content.ActivityNotFoundException].
 */
fun buildViewImageIntent(uri: Uri): Intent =
    Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, SavedImageSupport.MIME_TYPE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
