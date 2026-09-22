package com.r2manager.android.domain.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.r2manager.android.core.mime.MimeTypes

/**
 * 下载目标解析器（P3）。
 *
 * 统一走 SAF 目录树（见架构 §5.2）：首次由 UI 通过 `ACTION_OPEN_DOCUMENT_TREE` 获取并
 * `takePersistableUriPermission` 持久化；此后本解析器从系统持久化授权中读取，无需再问用户。
 *
 * 相对路径（批量下载保留目录结构）：按 `/` 逐级 `findFile`/`createDirectory` 建立，
 * 已存在同名文件时先删除再创建（覆盖语义）。
 */
class DownloadTargetResolver(private val context: Context) {

    /**
     * 读取已持久化的下载目录树 Uri。
     *
     * 优先返回系统 `persistedUriPermissions` 中可写的目录树授权；无授权时返回 null，
     * 由 UI 引导用户再次选择目录（R-30 兜底）。
     */
    fun resolveTreeUri(): Uri? {
        val resolver = context.contentResolver
        val permission = resolver.persistedUriPermissions.firstOrNull { it.isReadPermission && it.isWritePermission }
            ?: return null
        return permission.uri
    }

    /**
     * 是否已具备可用的下载目录授权。
     */
    fun hasPersistedPermission(): Boolean = resolveTreeUri() != null

    /**
     * 在给定目录树内创建（或覆盖）目标文件，返回其文档 Uri。
     *
     * @param treeUri 目录树根 Uri
     * @param relativePath 相对子目录（可空/空串 = 根目录），如 `photos/2026`
     * @param fileName 文件名（含扩展名）
     * @throws SecurityException 授权失效（目录被删/权限被回收）
     * @throws IllegalStateException 无法创建目录或文件
     */
    suspend fun createTarget(treeUri: Uri, relativePath: String?, fileName: String): Uri {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw SecurityException("无法访问下载目录，授权可能已失效")

        // 逐级建立相对目录
        var directory = root
        val segments = (relativePath ?: "").split('/').filter { it.isNotBlank() && it != "." && it != ".." }
        for (segment in segments) {
            val existing = directory.findFile(segment)
            directory = when {
                existing == null -> directory.createDirectory(segment)
                    ?: throw IllegalStateException("无法创建目录: $segment")
                existing.isDirectory -> existing
                else -> {
                    // 同名文件占位 → 删除后重建为目录
                    existing.delete()
                    directory.createDirectory(segment)
                        ?: throw IllegalStateException("无法创建目录: $segment")
                }
            }
        }

        // 覆盖：已存在同名文件先删
        directory.findFile(fileName)?.let { if (it.isFile) it.delete() }

        val mimeType = MimeTypes.resolveContentType(fileName)
        val created = directory.createFile(mimeType, fileName)
            ?: throw IllegalStateException("无法创建文件: $fileName")

        Log.d(TAG, "createTarget ok: ${created.uri}")
        return created.uri
    }

    /**
     * 以覆盖模式打开目标文档的输出流；授权失效时抛 [SecurityException]。
     */
    fun openOutputStream(targetUri: Uri) =
        context.contentResolver.openOutputStream(targetUri, "wt")
            ?: throw IllegalStateException("无法打开目标文件的写入流")

    /** 构造「重新选择目录」的系统 Intent（供 UI 使用）。 */
    fun buildPickTreeIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
    }

    private companion object {
        const val TAG = "DownloadTargetResolver"
    }
}
