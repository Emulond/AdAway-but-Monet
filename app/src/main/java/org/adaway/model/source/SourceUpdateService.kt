package org.adaway.model.source

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.adaway.AdAwayApplication
import org.adaway.helper.NotificationHelper
import org.adaway.helper.ProgressNotifications
import org.adaway.model.root.ProgressReporter
import org.adaway.R
import org.adaway.helper.PreferenceHelper
import org.adaway.model.error.HostErrorException
import timber.log.Timber
import java.util.concurrent.TimeUnit.HOURS

object SourceUpdateService {
    private const val WORK_NAME = "HostsUpdateWork"

    @JvmStatic
    fun enable(context: Context, unmeteredNetworkOnly: Boolean) {
        enqueueWork(context, ExistingPeriodicWorkPolicy.UPDATE, unmeteredNetworkOnly)
    }

    @JvmStatic
    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    @JvmStatic
    fun syncPreferences(context: Context) {
        if (PreferenceHelper.getUpdateCheckHostsDaily(context)) {
            enqueueWork(context, ExistingPeriodicWorkPolicy.KEEP, PreferenceHelper.getUpdateOnlyOnWifi(context))
        } else {
            disable(context)
        }
    }

    private fun enqueueWork(
        context: Context,
        workPolicy: ExistingPeriodicWorkPolicy,
        unmeteredNetworkOnly: Boolean
    ) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            workPolicy,
            getWorkRequest(unmeteredNetworkOnly)
        )
    }

    private fun getWorkRequest(unmeteredNetworkOnly: Boolean): PeriodicWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (unmeteredNetworkOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()
        return PeriodicWorkRequest.Builder(HostsSourcesUpdateWorker::class.java, 6, HOURS)
            .setConstraints(constraints)
            .setInitialDelay(3, HOURS)
            .build()
    }

    class HostsSourcesUpdateWorker(
        context: Context,
        workerParams: WorkerParameters
    ) : Worker(context, workerParams) {
        override fun doWork(): Result {
            Timber.i("Starting update worker")
            val application = applicationContext as AdAwayApplication
            val model = application.sourceModel
            val hasUpdate = try {
                ProgressNotifications.report(
                    application, ProgressNotifications.Kind.UPDATE_HOSTS, null, null, false
                )
                model.checkForUpdate { completed, total, _ ->
                    reportProgress(
                        application, completed, total,
                        R.string.notification_update_host_progress_check
                    )
                }
            } catch (exception: HostErrorException) {
                Timber.e(exception, "Failed to check for update. Will retry later.")
                ProgressNotifications.done(application, ProgressNotifications.Kind.UPDATE_HOSTS)
                return Result.retry()
            }

            if (hasUpdate) {
                return try {
                    doUpdate(application)
                    Result.success()
                } catch (exception: HostErrorException) {
                    Timber.e(exception, "Failed to apply hosts file during background update.")
                    Result.failure()
                }
            }
            ProgressNotifications.done(application, ProgressNotifications.Kind.UPDATE_HOSTS)
            return Result.success()
        }

        /**
         * Report the progress of a background update. Always notified: there is no screen showing
         * it, unlike an update started by the user.
         */
        private fun reportProgress(
            application: AdAwayApplication,
            completed: Int,
            total: Int,
            textRes: Int
        ) {
            ProgressNotifications.report(
                application,
                ProgressNotifications.Kind.UPDATE_HOSTS,
                ProgressReporter.percentOf(completed, total),
                application.getString(textRes, (completed + 1).coerceAtMost(total), total),
                false
            )
        }

        @Throws(HostErrorException::class)
        private fun doUpdate(application: AdAwayApplication) {
            if (PreferenceHelper.getAutomaticUpdateDaily(application)) {
                try {
                    application.sourceModel.retrieveHostsSources { completed, total, _ ->
                        reportProgress(
                            application, completed, total,
                            R.string.notification_update_host_progress_source
                        )
                    }
                    ProgressNotifications.report(
                        application,
                        ProgressNotifications.Kind.UPDATE_HOSTS,
                        100,
                        application.getString(R.string.notification_update_host_progress_apply),
                        false
                    )
                    application.adBlockModel.apply()
                } finally {
                    ProgressNotifications.done(application, ProgressNotifications.Kind.UPDATE_HOSTS)
                }
            } else {
                // The check notification must not outlive the check when no update is applied.
                ProgressNotifications.done(application, ProgressNotifications.Kind.UPDATE_HOSTS)
                NotificationHelper.showUpdateHostsNotification(application)
            }
        }
    }
}
