package com.r2manager.android.data.local.db

import android.content.ContentValues
import android.database.Cursor
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_BUCKET
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_CREATED_AT
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_DIRECTION
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_ERROR_MESSAGE
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_ERROR_TYPE
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_ID
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_KEY
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_LOCAL_URI
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_SIZE
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_STATUS
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_TRANSFERRED
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_UPDATED_AT
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_UPLOADED_PARTS
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.COL_UPLOAD_ID
import com.r2manager.android.data.local.db.AppDatabaseHelper.Companion.TASK_TABLE
import com.r2manager.android.domain.model.TransferStatus

/**
 * 传输任务 DAO（手写 SQL，零注解处理器）。
 *
 * 所有方法为主线程外调用（由 [com.r2manager.android.domain.transfer.TransferQueue] 在 IO 上调度）。
 */
class TransferTaskDao(private val helper: AppDatabaseHelper) {

    /** 插入；返回自增主键。 */
    fun insert(entity: TransferTaskEntity): Long {
        val values = toValues(entity)
        return helper.writableDatabase.insert(TASK_TABLE, null, values)
    }

    /** 全量更新一行（按 id）。 */
    fun update(entity: TransferTaskEntity) {
        val values = toValues(entity)
        helper.writableDatabase.update(
            TASK_TABLE,
            values,
            "$COL_ID = ?",
            arrayOf(entity.id.toString())
        )
    }

    /** 仅更新进度与状态（高频调用，避免整行写）。 */
    fun updateProgress(id: Long, transferred: Long, status: String, updatedAt: Long) {
        val values = ContentValues().apply {
            put(COL_TRANSFERRED, transferred)
            put(COL_STATUS, status)
            put(COL_UPDATED_AT, updatedAt)
        }
        helper.writableDatabase.update(
            TASK_TABLE,
            values,
            "$COL_ID = ?",
            arrayOf(id.toString())
        )
    }

    fun findById(id: Long): TransferTaskEntity? =
        query("$COL_ID = ?", arrayOf(id.toString())).firstOrNull()

    fun listByStatus(status: String): List<TransferTaskEntity> =
        query("$COL_STATUS = ?", arrayOf(status))

    fun listAll(): List<TransferTaskEntity> = query(null, null)

    fun delete(id: Long) {
        helper.writableDatabase.delete(TASK_TABLE, "$COL_ID = ?", arrayOf(id.toString()))
    }

    /** 删除终态任务（DONE / CANCELLED）。 */
    fun deleteCompleted() {
        helper.writableDatabase.delete(
            TASK_TABLE,
            "$COL_STATUS IN (?, ?)",
            arrayOf(TransferStatus.DONE.name, TransferStatus.CANCELLED.name)
        )
    }

    fun deleteAll() {
        helper.writableDatabase.delete(TASK_TABLE, null, null)
    }

    /**
     * 进程被杀后恢复：把 RUNNING/QUEUED 全部置为 FAILED（可重试），满足 R-33。
     *
     * @param now 当前时间（epoch millis）
     * @return 受影响行数
     */
    fun markInterruptedAsFailed(now: Long): Int {
        val values = ContentValues().apply {
            put(COL_STATUS, TransferStatus.FAILED.name)
            put(COL_UPDATED_AT, now)
        }
        return helper.writableDatabase.update(
            TASK_TABLE,
            values,
            "$COL_STATUS IN (?, ?)",
            arrayOf(TransferStatus.RUNNING.name, TransferStatus.QUEUED.name)
        )
    }

    private fun query(selection: String?, selectionArgs: Array<String>?): List<TransferTaskEntity> {
        val db = helper.readableDatabase
        val orderBy = "$COL_CREATED_AT DESC, $COL_ID DESC"
        db.query(TASK_TABLE, null, selection, selectionArgs, null, null, orderBy).use { cursor ->
            val result = ArrayList<TransferTaskEntity>(cursor.count)
            while (cursor.moveToNext()) {
                result.add(fromCursor(cursor))
            }
            return result
        }
    }

    private fun fromCursor(cursor: Cursor): TransferTaskEntity = TransferTaskEntity(
        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
        direction = cursor.getString(cursor.getColumnIndexOrThrow(COL_DIRECTION)).orEmpty(),
        bucket = cursor.getString(cursor.getColumnIndexOrThrow(COL_BUCKET)).orEmpty(),
        key = cursor.getString(cursor.getColumnIndexOrThrow(COL_KEY)).orEmpty(),
        localUri = cursor.getString(cursor.getColumnIndexOrThrow(COL_LOCAL_URI)).orEmpty(),
        size = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SIZE)),
        transferred = cursor.getLong(cursor.getColumnIndexOrThrow(COL_TRANSFERRED)),
        status = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)).orEmpty(),
        errorMessage = cursor.getStringOrNull(COL_ERROR_MESSAGE),
        errorType = cursor.getStringOrNull(COL_ERROR_TYPE),
        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)),
        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED_AT)),
        uploadId = cursor.getStringOrNull(COL_UPLOAD_ID),
        uploadedParts = cursor.getInt(cursor.getColumnIndexOrThrow(COL_UPLOADED_PARTS))
    )

    private fun toValues(entity: TransferTaskEntity): ContentValues =
        ContentValues().apply {
            put(COL_DIRECTION, entity.direction)
            put(COL_BUCKET, entity.bucket)
            put(COL_KEY, entity.key)
            put(COL_LOCAL_URI, entity.localUri)
            put(COL_SIZE, entity.size)
            put(COL_TRANSFERRED, entity.transferred)
            put(COL_STATUS, entity.status)
            put(COL_ERROR_MESSAGE, entity.errorMessage)
            put(COL_ERROR_TYPE, entity.errorType)
            put(COL_CREATED_AT, entity.createdAt)
            put(COL_UPDATED_AT, entity.updatedAt)
            put(COL_UPLOAD_ID, entity.uploadId)
            put(COL_UPLOADED_PARTS, entity.uploadedParts)
        }

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }
}
