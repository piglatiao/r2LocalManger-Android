package com.r2manager.android

import android.app.Application
import android.content.Context
import com.r2manager.android.core.log.AppLog

/**
 * 应用入口。
 *
 * 仅做两件事：初始化日志、创建 [AppContainer]。应用锁 bootstrap 由启动页（P4）触发，
 * 以避免在此处引入生命周期依赖。
 */
class R2ManagerApp : Application() {

    /** 全生命周期单例容器。 */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        container = AppContainer(this)
        AppLog.i(TAG, "R2ManagerApp 初始化完成")
    }

    companion object {
        private const val TAG = "R2ManagerApp"
    }
}

/**
 * 便捷取容器。
 */
fun Context.appContainer(): AppContainer =
    (applicationContext as R2ManagerApp).container
