package com.r2manager.android.data.local.db

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r2manager.android.domain.model.TransferDirection
import com.r2manager.android.domain.model.TransferStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 传输任务 DAO 仪器测试（需设备/模拟器，`./gradlew :app:connectedDebugAndroidTest`）。
 */
@RunWith(AndroidJUnit4::class)
class TransferTaskDaoTest {

    private lateinit var helper: AppDatabaseHelper
    private lateinit var dao: TransferTaskDao

    @Before
    fun setUp() {
        helper = AppDatabaseHelper(ApplicationProvider.getApplicationContext())
        dao = TransferTaskDao(helper)
        dao.deleteAll()
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun insertFindUpdateDelete() {
        val id = dao.insert(entity(status = TransferStatus.QUEUED, key = "a.txt"))
        assertEquals(1, dao.listAll().size)

        val loaded = dao.findById(id)
        assertNotNull(loaded)
        assertEquals("a.txt", loaded!!.key)
        assertEquals(TransferStatus.QUEUED.name, loaded.status)

        dao.update(loaded.copy(transferred = 50L, status = TransferStatus.RUNNING.name))
        val updated = dao.findById(id)!!
        assertEquals(50L, updated.transferred)
        assertEquals(TransferStatus.RUNNING.name, updated.status)

        dao.delete(id)
        assertNull(dao.findById(id))
        assertEquals(0, dao.listAll().size)
    }

    @Test
    fun updateProgressKeepsOtherColumns() {
        val id = dao.insert(entity(status = TransferStatus.RUNNING, key = "big.bin", size = 1000L))
        dao.updateProgress(id = id, transferred = 512L, status = TransferStatus.RUNNING.name, updatedAt = 4242L)

        val loaded = dao.findById(id)!!
        assertEquals(512L, loaded.transferred)
        assertEquals(1000L, loaded.size)
        assertEquals(4242L, loaded.updatedAt)
        assertEquals("big.bin", loaded.key)
    }

    @Test
    fun listByStatusFiltersAndListAllOrdersByCreatedAtDesc() {
        dao.insert(entity(status = TransferStatus.QUEUED, key = "q", createdAt = 100L))
        dao.insert(entity(status = TransferStatus.DONE, key = "d", createdAt = 200L))

        assertEquals(1, dao.listByStatus(TransferStatus.QUEUED.name).size)
        assertEquals(2, dao.listAll().size)
        // 新任务在前
        assertEquals("d", dao.listAll().first().key)
    }

    @Test
    fun markInterruptedAsFailedConvertsQueuedAndRunning() {
        val queued = dao.insert(entity(status = TransferStatus.QUEUED, key = "q"))
        val running = dao.insert(entity(status = TransferStatus.RUNNING, key = "r"))
        val done = dao.insert(entity(status = TransferStatus.DONE, key = "d"))

        val affected = dao.markInterruptedAsFailed(now = 9999L)

        assertEquals(2, affected)
        assertEquals(TransferStatus.FAILED.name, dao.findById(queued)!!.status)
        assertEquals(TransferStatus.FAILED.name, dao.findById(running)!!.status)
        assertEquals(9999L, dao.findById(running)!!.updatedAt)
        assertEquals(TransferStatus.DONE.name, dao.findById(done)!!.status)
    }

    @Test
    fun deleteCompletedRemovesOnlyTerminalStates() {
        val queued = dao.insert(entity(status = TransferStatus.QUEUED, key = "q"))
        dao.insert(entity(status = TransferStatus.DONE, key = "d"))
        dao.insert(entity(status = TransferStatus.CANCELLED, key = "c"))
        dao.insert(entity(status = TransferStatus.FAILED, key = "f"))

        dao.deleteCompleted()

        val remaining = dao.listAll().map { it.key }.toSet()
        assertEquals(setOf("q", "f"), remaining)
        assertNotNull(dao.findById(queued))
    }

    private fun entity(
        status: TransferStatus,
        key: String,
        size: Long = 0L,
        createdAt: Long = System.currentTimeMillis()
    ) = TransferTaskEntity(
        id = 0L,
        direction = TransferDirection.UPLOAD.name,
        bucket = "bucket",
        key = key,
        localUri = "content://local/$key",
        size = size,
        transferred = 0L,
        status = status.name,
        errorMessage = null,
        errorType = null,
        createdAt = createdAt,
        updatedAt = createdAt,
        uploadId = null,
        uploadedParts = 0
    )
}
