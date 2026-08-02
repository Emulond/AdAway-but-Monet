package org.adaway.model.source

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
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

    /**
     * The unique name of an update the user asked for.
     */
    private const val MANUAL_WORK_NAME = "HostsUpdateNow"

    /**
     * Whether the manual update should retrieve the sources without checking them first.
     */
    const val KEY_SKIP_CHECK = "skipCheck"

    /**
     * Set on success when the check found every source already up to date.
     */
    const val KEY_UP_TO_DATE = "upToDate"

    /**
     * Set on failure, naming the error that stopped the update.
     */
    const val KEY_ERROR = "error"

    /**
     * The intervals offered to the user, in hours.
     */
    @JvmField
    val UPDATE_INTERVALS_HOURS = intArrayOf(6, 12, 24, 48, 168)

    @JvmStatic
    fun enable(context: Context, unmeteredNetworkOnly: Boolean) {
        enqueueWork(context, ExistingPeriodicWorkPolicy.UPDATE, unmeteredNetworkOnly, context.intervalHours())
    }

    /**
     * Run an update now, on behalf of the user.
     *
     * Enqueued rather than run on the caller: it used to run in the scope of the screen that
     * started it, so leaving that screen cancelled the update part way through.
     *
     * @param skipCheck Retrieve the sources without checking them for update first.
     */
    @JvmStatic
    fun runNow(context: Context, skipCheck: Boolean) {
        val request = OneTimeWorkRequest.Builder(ManualUpdateWorker::class.java)
            .setInputData(Data.Builder().putBoolean(KEY_SKIP_CHECK, skipCheck).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Observe the update the user asked for.
     */
    @JvmStatic
    fun observeManualWork(context: Context): LiveData<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(MANUAL_WORK_NAME)

    @JvmStatic
    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    @JvmStatic
    fun syncPreferences(context: Context) {
        if (PreferenceHelper.getUpdateCheckHostsDaily(context)) {
            enqueueWork(
                context,
                ExistingPeriodicWorkPolicy.KEEP,
                PreferenceHelper.getUpdateOnlyOnWifi(context),
                context.intervalHours()
            )
        } else {
            disable(context)
        }
    }

    private fun Context.intervalHours(): Int =
        PreferenceHelper.getUpdateIntervalHours(this)
            .takeIf { it in UPDATE_INTERVALS_HOURS }
            ?: PreferenceHelper.DEFAULT_UPDATE_INTERVAL_HOURS

    /**
     * Re-enqueue the work with the current settings, replacing any previously scheduled one.
     * Called when a setting that shapes the schedule changes.
     */
    @JvmStatic
    fun reschedule(context: Context) {
        if (PreferenceHelper.getUpdateCheckHostsDaily(context)) {
            enqueueWork(
                context,
                ExistingPeriodicWorkPolicy.UPDATE,
                PreferenceHelper.getUpdateOnlyOnWifi(context),
                context.intervalHours()
            )
        } else {
            disable(context)
        }
    }

    private fun enqueueWork(
        context: Context,
        workPolicy: ExistingPeriodicWorkPolicy,
        unmeteredNetworkOnly: Boolean,
        intervalHours: Int
    ) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            workPolicy,
            getWorkRequest(unmeteredNetworkOnly, intervalHours)
        )
    }

    private fun getWorkRequest(unmeteredNetworkOnly: Boolean, intervalHours: Int): PeriodicWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (unmeteredNetworkOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()
        return PeriodicWorkRequest.Builder(
            HostsSourcesUpdateWorker::class.java,
            intervalHours.toLong(),
            HOURS
        )
            .setConstraints(constraints)
            // Start no earlier than half an interval, so enabling it does not run immediately.
            .setInitialDelay((intervalHours / 2).coerceAtLeast(1).toLong(), HOURS)
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

    /**
     * Runs an update the user asked for.
     *
     * It reports progress the same way the scheduled update does, but its notification is withheld
     * while the application is in the foreground, since the screen shows the same progress.
     */
    class ManualUpdateWorker(
        context: Context,
        workerParams: WorkerParameters
    ) : Worker(context, workerParams) {
        override fun doWork(): Result {
            val application = applicationContext as AdAwayApplication
            val skipCheck = inputData.getBoolean(KEY_SKIP_CHECK, false)
            ProgressNotifications.report(
                application, ProgressNotifications.Kind.UPDATE_HOSTS, null
            )
            return try {
                if (!skipCheck) {
                    val hasUpdate = application.sourceModel.checkForUpdate { completed, total, _ ->
                        report(application, completed, total, R.string.notification_update_host_progress_check)
                    }
                    if (!hasUpdate) {
                        return Result.success(
                            Data.Builder().putBoolean(KEY_UP_TO_DATE, true).build()
                        )
                    }
                }
                application.sourceModel.retrieveHostsSources { completed, total, _ ->
                    report(application, completed, total, R.string.notification_update_host_progress_source)
                }
                ProgressNotifications.report(
                    application,
                    ProgressNotifications.Kind.UPDATE_HOSTS,
                    100,
                    application.getString(R.string.notification_update_host_progress_apply)
                )
                application.adBlockModel.apply()
                Result.success()
            } catch (exception: HostErrorException) {
                Timber.w(exception, "Failed to run the requested update.")
                Result.failure(Data.Builder().putString(KEY_ERROR, exception.error.name).build())
            } finally {
                ProgressNotifications.done(application, ProgressNotifications.Kind.UPDATE_HOSTS)
            }
        }

        private fun report(
            application: AdAwayApplication,
            completed: Int,
            total: Int,
            textRes: Int
        ) {
            ProgressNotifications.report(
                application,
                ProgressNotifications.Kind.UPDATE_HOSTS,
                ProgressReporter.percentOf(completed, total),
                application.getString(textRes, (completed + 1).coerceAtMost(total), total)
            )
        }
    }
}
