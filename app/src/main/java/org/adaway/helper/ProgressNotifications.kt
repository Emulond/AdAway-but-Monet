package org.adaway.helper

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the progress notifications of the long running operations.
 *
 * An operation started from the screen already reports its progress there, so its notification is
 * withheld while the application is in the foreground and posted as soon as the user leaves. An
 * operation started in the background, such as the scheduled update, has no screen to report to and
 * is always notified.
 *
 * This also owns their clean up. The notifications are ongoing, so one posted by a process that is
 * later killed would stay on screen with nobody left to cancel it. Every one of them is therefore
 * cleared when the process starts, and again whenever the user opens the application.
 */
object ProgressNotifications {
    enum class Kind {
        UPDATE_HOSTS,
        APPLY_CONFIGURATION
    }

    /**
     * @param percent The completion, or {@code null} when it is not known yet.
     * @param text The notification text.
     * @param hideInForeground Whether the screen reports this progress itself.
     */
    private data class Progress(
        val percent: Int?,
        val text: String?,
        val hideInForeground: Boolean
    )

    private val running = ConcurrentHashMap<Kind, Progress>()

    @Volatile
    private var foreground = false

    /**
     * Clear anything left behind by a previous process and start tracking the application state.
     * Called once, when the application is created.
     */
    @JvmStatic
    fun init(context: Context) {
        val applicationContext = context.applicationContext
        // Nothing can be running at process start, so anything still showing is a leftover.
        for (kind in Kind.entries) {
            cancel(applicationContext, kind)
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground = true
                for ((kind, progress) in running) {
                    if (progress.hideInForeground) {
                        cancel(applicationContext, kind)
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                foreground = false
                // Replay what is still running, so leaving the application does not hide it.
                for ((kind, progress) in running) {
                    post(applicationContext, kind, progress)
                }
            }
        })
    }

    /**
     * Report the progress of an operation.
     *
     * @param kind Which operation is progressing.
     * @param percent The completion, or {@code null} when it is not known yet.
     * @param text The notification text, or {@code null} for the default one.
     * @param hideInForeground Whether the screen reports this progress itself.
     */
    @JvmStatic
    @JvmOverloads
    fun report(
        context: Context,
        kind: Kind,
        percent: Int?,
        text: String? = null,
        hideInForeground: Boolean = true
    ) {
        val progress = Progress(percent, text, hideInForeground)
        running[kind] = progress
        val applicationContext = context.applicationContext
        if (foreground && hideInForeground) {
            cancel(applicationContext, kind)
        } else {
            post(applicationContext, kind, progress)
        }
    }

    /**
     * Report that an operation ended, whatever the outcome.
     * Safe to call for an operation that was never reported as running.
     */
    @JvmStatic
    fun done(context: Context, kind: Kind) {
        running.remove(kind)
        cancel(context.applicationContext, kind)
    }

    private fun post(context: Context, kind: Kind, progress: Progress) {
        when (kind) {
            Kind.UPDATE_HOSTS -> NotificationHelper.showUpdateHostsProgressNotification(
                context, progress.percent, progress.text
            )

            Kind.APPLY_CONFIGURATION -> NotificationHelper.showApplyConfigurationProgressNotification(
                context, progress.percent ?: 0
            )
        }
    }

    private fun cancel(context: Context, kind: Kind) {
        when (kind) {
            Kind.UPDATE_HOSTS -> NotificationHelper.clearUpdateHostsProgressNotification(context)
            Kind.APPLY_CONFIGURATION -> NotificationHelper.clearApplyConfigurationNotification(context)
        }
    }
}
