package io.github.sunken1110.tandemplanner.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Background job for the widget, run by WorkManager (which keeps it scheduled
 * across reboots and runs it even when the app is closed).
 * - [KEY_SYNC] true: read the server, save, redraw (hourly).
 * - [KEY_SYNC] false: only redraw (midnight / an event's time passing).
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (inputData.getBoolean(KEY_SYNC, false)) {
            WidgetUpdater.syncFromServer(applicationContext)
        } else {
            WidgetUpdater.render(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val KEY_SYNC = "sync"
    }
}
