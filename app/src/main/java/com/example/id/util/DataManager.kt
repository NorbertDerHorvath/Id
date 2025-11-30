package com.example.id.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.example.id.data.AppDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DataManager(private val context: Context) {

    private val dbName = "tfm-database"

    fun backupDatabase() {
        try {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) {
                Toast.makeText(context, "Adatbázis nem található.", Toast.LENGTH_SHORT).show()
                return
            }

            val fileName = "TFM_Backup_${System.currentTimeMillis()}.db"
            val resolver = context.contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    dbFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    Toast.makeText(context, "Adatmentés sikeres a Letöltések mappába.", Toast.LENGTH_LONG).show()
                }
            } ?: run {
                throw Exception("MediaStore URI létrehozása sikertelen.")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Adatmentés sikertelen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun restoreDatabase(backupUri: Uri): Boolean {
        val dbFile = context.getDatabasePath(dbName)

        try {
            // Bezárjuk az adatbázist, hogy felül lehessen írni
            AppDatabase.getDatabase(context).close()

            context.contentResolver.openInputStream(backupUri)?.use { inputStream ->
                FileOutputStream(dbFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Adatvisszaállítás sikertelen: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
    }
}
