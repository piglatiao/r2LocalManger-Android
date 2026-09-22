package com.r2manager.android.data.local.cache

import android.content.Context
import java.io.File

/**
 * 缓存目录布局（应用私有目录，不落明文）。
 *
 * ```
 * files/cache/
 *   ├── thumb_index.json   # 缩略图 LRU 索引
 *   ├── list_index.json    # 对象列表 LRU 索引
 *   ├── thumbs/<id>.bin    # 缩略图密文（R2CE 格式）
 *   └── lists/<id>.json    # 列表密文（R2CE 格式）
 * ```
 *
 * 所有内容经 AES-256-GCM 加密落盘；无密钥时**不写缓存**（由 CryptoFileStore 保证）。
 */
object CachePaths {

    const val ROOT_DIR = "cache"
    const val THUMB_DIR = "thumbs"
    const val LIST_DIR = "lists"
    const val THUMB_INDEX = "thumb_index.json"
    const val LIST_INDEX = "list_index.json"
    const val THUMB_EXT = ".bin"
    const val LIST_EXT = ".json"

    /** 缓存根目录。 */
    fun root(context: Context): File = File(context.filesDir, ROOT_DIR)

    /** 缩略图目录。 */
    fun thumbnailsDir(root: File): File = File(root, THUMB_DIR)

    /** 列表目录。 */
    fun listsDir(root: File): File = File(root, LIST_DIR)

    /** 缩略图索引文件。 */
    fun thumbIndexFile(root: File): File = File(root, THUMB_INDEX)

    /** 列表索引文件。 */
    fun listIndexFile(root: File): File = File(root, LIST_INDEX)

    /** 缩略图相对路径：`thumbs/<id>.bin`。 */
    fun thumbRelPath(id: String): String = "$THUMB_DIR/$id$THUMB_EXT"

    /** 列表相对路径：`lists/<id>.json`。 */
    fun listRelPath(id: String): String = "$LIST_DIR/$id$LIST_EXT"

    /** 缩略图绝对文件。 */
    fun thumbFile(root: File, id: String): File = File(root, thumbRelPath(id))

    /** 列表绝对文件。 */
    fun listFile(root: File, id: String): File = File(root, listRelPath(id))

    /** 确保全部缓存子目录存在。 */
    fun ensureDirs(root: File) {
        root.mkdirs()
        thumbnailsDir(root).mkdirs()
        listsDir(root).mkdirs()
    }
}
