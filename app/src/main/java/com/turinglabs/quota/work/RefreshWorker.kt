package com.turinglabs.quota.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.turinglabs.quota.data.QuotaClient
import com.turinglabs.quota.data.QuotaError
import com.turinglabs.quota.data.QuotaStore
import com.turinglabs.quota.widget.QuotaWidget
import java.util.concurrent.TimeUnit

/** Fetches the usage for the widget every 15 minutes (the shortest period WorkManager allows). */
class RefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val store = QuotaStore(applicationContext)
        if (store.isPaired && !store.showsSample) {
            try {
                QuotaClient(store).fetchUsage()
            } catch (error: QuotaError.Unauthorized) {
                store.unpair()
            } catch (error: QuotaError) {
                // Keep the cached numbers; the widget shows how old they are.
            }
        }
        QuotaWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("quota-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork("quota-refresh-now", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
