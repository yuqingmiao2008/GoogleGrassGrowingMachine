package io.github.yuqingmiao2008.grassmachine.core

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 文件保存：Android 10+ 走 MediaStore（无需存储权限）写入 手机存储/Download；
 * Android 9 及以下直接写公共 Download 目录（需要存储权限）。
 */
object Saver {

    data class Saved(val uri: Uri?, val displayPath: String, val file: File? = null)

    fun subPath(useSubdir: Boolean): String =
        if (useSubdir) "${Environment.DIRECTORY_DOWNLOADS}/谷歌生草机" else Environment.DIRECTORY_DOWNLOADS

    fun uniqueName(prefix: String, ext: String): String {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${prefix}_$ts.$ext"
    }

    fun save(
        context: Context,
        fileName: String,
        mime: String,
        useSubdir: Boolean,
        producer: (OutputStream) -> Unit,
    ): Saved {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, subPath(useSubdir))
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("无法在 Download 目录创建文件")
            resolver.openOutputStream(uri)?.use(producer)
                ?: throw IllegalStateException("无法打开输出流")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Saved(uri, "Download/${if (useSubdir) "谷歌生草机/" else ""}$fileName")
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                if (useSubdir) "谷歌生草机" else "",
            )
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, fileName)
            FileOutputStream(f).use(producer)
            MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), arrayOf(mime), null)
            Saved(Uri.fromFile(f), f.absolutePath, f)
        }
    }

    fun saveBytes(
        context: Context,
        fileName: String,
        mime: String,
        useSubdir: Boolean,
        bytes: ByteArray,
    ): Saved = save(context, fileName, mime, useSubdir) { it.write(bytes) }

    /** 打包 zip：entries 为 (文件名, 字节) */
    fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            entries.forEach { (name, data) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }
}
