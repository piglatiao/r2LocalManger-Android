package com.r2manager.android.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 传输任务数据库（手写 [SQLiteOpenHelper]，零注解处理器）。
 *
 * 单表 `transfer_task`，`status` 建索引以支持按状态查询与「中断任务恢复」。
 */
class AppDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_TABLE)
        db.execSQL(CREATE_STATUS_INDEX)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 一期只有 v1，升级策略：安全重建（任务为可重试的一次性数据）。
        db.execSQL("DROP TABLE IF EXISTS $TASK_TABLE")
        onCreate(db)
    }

    companion object {
        const val DB_NAME = "r2_transfer.db"
        const val DB_VERSION = 1
        const val TASK_TABLE = "transfer_task"

        const val COL_ID = "id"
        const val COL_DIRECTION = "direction"
        const val COL_BUCKET = "bucket"
        const val COL_KEY = "obj_key"
        const val COL_LOCAL_URI = "local_uri"
        const val COL_SIZE = "size"
        const val COL_TRANSFERRED = "transferred"
        const val COL_STATUS = "status"
        const val COL_ERROR_MESSAGE = "error_message"
        const val COL_ERROR_TYPE = "error_type"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_UPLOAD_ID = "upload_id"
        const val COL_UPLOADED_PARTS = "uploaded_parts"

        private const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS $TASK_TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DIRECTION TEXT NOT NULL,
                $COL_BUCKET TEXT NOT NULL,
                $COL_KEY TEXT NOT NULL,
                $COL_LOCAL_URI TEXT NOT NULL,
                $COL_SIZE INTEGER NOT NULL DEFAULT 0,
                $COL_TRANSFERRED INTEGER NOT NULL DEFAULT 0,
                $COL_STATUS TEXT NOT NULL,
                $COL_ERROR_MESSAGE TEXT,
                $COL_ERROR_TYPE TEXT,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_UPLOAD_ID TEXT,
                $COL_UPLOADED_PARTS INTEGER NOT NULL DEFAULT 0
            )
        """

        private const val CREATE_STATUS_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_${TASK_TABLE}_$COL_STATUS ON $TASK_TABLE($COL_STATUS)"
    }
}
