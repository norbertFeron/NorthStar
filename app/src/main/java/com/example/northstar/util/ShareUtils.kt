package com.example.northstar.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Hands a file in `cacheDir/shared/` to the system share sheet via the app's FileProvider. */
object ShareUtils {
    fun shareFile(context: Context, file: File, mime: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
