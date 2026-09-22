package com.r2manager.android.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/**
 * `content://` / SAF Uri 元信息读取与持久化授权工具。
 *
 * 仅处理可公开读取的元信息（展示名 / 大小 / MIME），不读取内容本身。
 */
object UriUtils {

    /**
     * 读取展示名（`OpenableColumns.DISPLAY_NAME`）。
     *
     * @param ctx 上下文
     * @param uri 内容 Uri
     * @return 展示名；读取失败返回 null
     */
    fun queryDisplayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }.getOrNull()

    /**
     * 读取文件大小（`OpenableColumns.SIZE`，失败时回退 [android.content.res.AssetFileDescriptor.length]）。
     *
     * @param ctx 上下文
     * @param uri 内容 Uri
     * @return 字节数；未知返回 0
     */
    fun querySize(ctx: Context, uri: Uri): Long {
        val fromCursor = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()
        if (fromCursor != null && fromCursor > 0) {
            return fromCursor
        }
        val fromFd = runCatching {
            ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd -> afd.length }
        }.getOrNull()
        return (fromFd ?: 0L).coerceAtLeast(0L)
    }

    /**
     * 读取 MIME 类型。
     *
     * @param ctx 上下文
     * @param uri 内容 Uri
     */
    fun mimeType(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.getType(uri)
    }.getOrNull()

    /**
     * 持久化 SAF 树授权。
     *
     * @param ctx 上下文
     * @param treeUri 目录树 Uri
     * @param flags Intent 返回的授权标志（如 `FLAG_GRANT_READ_URI_PERMISSION`）
     */
    fun persistTreePermission(ctx: Context, treeUri: Uri, flags: Int) {
        val takeFlags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        runCatching { ctx.contentResolver.takePersistableUriPermission(treeUri, takeFlags) }
    }

    /**
     * 释放 SAF 树授权。
     *
     * @param ctx 上下文
     * @param treeUri 目录树 Uri
     */
    fun releasePersistablePermission(ctx: Context, treeUri: Uri) {
        runCatching {
            ctx.contentResolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }
}
