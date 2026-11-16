/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.2.0: after Metamatik (app/src/main/kotlin/com/bobbyesp/metadator/ExternalEditorActivity.kt),
 * a fork of Metadator, under the AGPL-3.0; written again on Nağmedar's components.
 */
package app.nagmedar.ui.tags

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.nagmedar.tags.ContentRef
import app.nagmedar.ui.components.NoticeHost
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.input.hasPrecisePointer
import app.nagmedar.ui.onboarding.MediaAccess
import app.nagmedar.ui.rememberNagmedarPreferences
import app.nagmedar.ui.theme.NagmedarTheme
import java.io.File

/**
 * 1.2.0, Metamatik's "Edit with" in Nağmedar: the tag editor for an audio file another app hands over (a file manager's
 * Edit or Share, several shared at once), in a window of its own; closing it goes back to that app. A file the system
 * has a path for is written as a file where the app may write every file (the library is then told of it); otherwise
 * through the other app's grant.
 */
class ExternalTagEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val targets = intent.audioUris().mapNotNull { target(this, it) }.distinct()
        if (targets.isEmpty()) {
            finish()
            return
        }
        val mode = if (hasPrecisePointer(this)) InputMode.Pointer else InputMode.Touch
        setContent {
            val prefs = rememberNagmedarPreferences()
            NagmedarTheme(themeMode = prefs.themeMode, accent = prefs.accent, inputMode = mode) {
                Box(Modifier.fillMaxSize()) {
                    TagEditorPage(targets, onClose = ::finish)
                    // the notices over the editor's foot: "Kaydedildi · Geri al"
                    NoticeHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 72.dp))
                }
            }
        }
    }

    private fun Intent.audioUris(): List<Uri> = when (action) {
        Intent.ACTION_SEND -> listOfNotNull(stream())
        Intent.ACTION_SEND_MULTIPLE -> streams()
        else -> listOfNotNull(data)
    }

    private fun Intent.stream(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_STREAM)

    private fun Intent.streams(): List<Uri> =
        if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else @Suppress("DEPRECATION") getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()

    internal companion object {
        /**
         * The file at [uri] for the editor: its path where the system has one and the app may write every file (written
         * as the library's are), else the address itself, written through its grant.
         */
        fun target(context: Context, uri: Uri): TagTarget? {
            val name = displayName(context, uri)
            if (uri.scheme == "file") return TagTarget.of(uri.toString(), name)
            if (uri.scheme != "content") return null
            val path = if (MediaAccess.hasAllFiles(context)) runCatching { filePath(context, uri) }.getOrNull() else null
            val file = path?.let(::File)?.takeIf { it.isFile && it.canRead() }
            if (file != null) return TagTarget.of(Uri.fromFile(file).toString(), name ?: file.name)
            return TagTarget(ContentRef(uri.toString()), name ?: uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString())
        }

        private fun displayName(context: Context, uri: Uri): String? = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }

        /** Where [uri]'s file is: the media store's column, or a storage document's id ("primary:Music/a.flac"). */
        private fun filePath(context: Context, uri: Uri): String? {
            if (uri.authority == "com.android.externalstorage.documents" && DocumentsContract.isDocumentUri(context, uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val volume = id.substringBefore(':')
                val rest = id.substringAfter(':', "")
                return if (volume.equals("primary", ignoreCase = true)) "/storage/emulated/0/$rest" else "/storage/$volume/$rest"
            }
            @Suppress("DEPRECATION")
            val column = MediaStore.MediaColumns.DATA
            return context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }
    }
}
