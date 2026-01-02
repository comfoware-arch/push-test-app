package com.restaurant.manageralerts.work

import android.content.Context
import androidx.work.*
import com.restaurant.manageralerts.data.SupabaseApi
import com.restaurant.manageralerts.util.DeviceId
import java.util.concurrent.TimeUnit

class ClaimWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val requestId = inputData.getString(KEY_REQUEST_ID) ?: return Result.failure()

        val prefs = applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("name", "") ?: ""
        val deviceId = DeviceId.get(applicationContext)

        // This returns true even if already taken; we just want it gone locally.
        SupabaseApi.claimCall(requestId = requestId, deviceId = deviceId, name = name)

        return Result.success()
    }

    companion object {
        private const val KEY_REQUEST_ID = "requestId"

        fun enqueue(ctx: Context, requestId: String) {
            val work = OneTimeWorkRequestBuilder<ClaimWorker>()
                .setInputData(workDataOf(KEY_REQUEST_ID to requestId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "claim_$requestId",
                ExistingWorkPolicy.KEEP,
                work
            )
        }
    }
}
