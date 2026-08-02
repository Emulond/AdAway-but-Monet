package org.adaway.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.AdAwayApplication
import org.adaway.db.AppDatabase
import org.adaway.db.HostCounts
import org.adaway.db.dao.MetadataDao
import org.adaway.db.entity.ListType
import org.adaway.db.dao.HostsSourceDao
import androidx.annotation.StringRes
import org.adaway.R
import org.adaway.helper.ProgressNotifications
import org.adaway.helper.PreferenceHelper
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.model.adblocking.AdBlockModel
import org.adaway.model.error.HostError
import org.adaway.model.error.HostErrorException
import org.adaway.db.entity.HostsSource
import org.adaway.model.source.SourceModel
import org.adaway.model.root.ProgressReporter
import org.adaway.model.source.SourceUpdateStatus
import org.adaway.model.update.Manifest
import org.adaway.model.update.UpdateModel
import org.adaway.vpn.VpnStatusRepository
import timber.log.Timber

/**
 * This class is an [AndroidViewModel] for the [HomeActivity] cards.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val sourceModel: SourceModel
    private val adBlockModel: AdBlockModel
    private val updateModel: UpdateModel

    private val database: AppDatabase
    private val hostsSourceDao: HostsSourceDao
    private val metadataDao: MetadataDao

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending

    private val _error = MutableSharedFlow<HostError>()
    val error: SharedFlow<HostError> = _error

    init {
        val awayApplication = application as AdAwayApplication
        sourceModel = awayApplication.sourceModel
        adBlockModel = awayApplication.adBlockModel
        updateModel = awayApplication.updateModel

        database = AppDatabase.getInstance(application)
        hostsSourceDao = database.hostsSourceDao()
        metadataDao = database.metadataDao()

        refreshHostCounts()

        VpnStatusRepository.update(PreferenceHelper.getVpnServiceStatus(application))
    }

    val state: StateFlow<String> = merge(
        sourceModel.state.asFlow(),
        adBlockModel.state.asFlow()
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), "")

    val adBlocked: StateFlow<Boolean> = combine(
        adBlockModel.isApplied.asFlow().map { it == true },
        VpnStatusRepository.status
    ) { applied, vpnStatus ->
        if (adBlockModel.method == AdBlockMethod.VPN) {
            vpnStatus.isStarted
        } else {
            applied
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS),
        adBlockModel.isApplied.value == true
    )

    val updateAvailable: StateFlow<Boolean> = sourceModel.isUpdateAvailable
        .asFlow()
        .map { it == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), false)

    val versionName: String get() = updateModel.versionName

    val appManifest: StateFlow<Manifest?> = updateModel.manifest
        .asFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), updateModel.manifest.value)

    /**
     * The host counters, read from their cached values so the screen shows them at once rather
     * than counting distinct hosts across millions of rows on every display.
     */
    private fun cachedHostCount(type: ListType): StateFlow<Int> = metadataDao.observeHostCount(type)
        .asFlow()
        .map { it?.toIntOrNull() ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), 0)

    val blockedHostCount: StateFlow<Int> = cachedHostCount(ListType.BLOCKED)

    val allowedHostCount: StateFlow<Int> = cachedHostCount(ListType.ALLOWED)

    val redirectHostCount: StateFlow<Int> = cachedHostCount(ListType.REDIRECTED)

    /**
     * The enabled sources, classified into up to date and outdated.
     * Both counts come from the same list so they always add up to the number of enabled sources.
     */
    private val enabledSources = hostsSourceDao.loadAll()
        .asFlow()
        .map { sources -> sources.filter { it.isEnabled } }

    val upToDateSourceCount: StateFlow<Int> = enabledSources
        .map { sources -> sources.count { it.isUpToDate() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), 0)

    val outdatedSourceCount: StateFlow<Int> = enabledSources
        .map { sources -> sources.count { !it.isUpToDate() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(FLOW_STOP_TIMEOUT_MILLIS), 0)

    private fun HostsSource.isUpToDate(): Boolean =
        SourceUpdateStatus.isUpToDate(localModificationDate, onlineModificationDate)

    /**
     * Recompute the cached host counters off the main thread.
     */
    private fun refreshHostCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            HostCounts.refresh(database)
        }
    }

    fun checkForAppUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            updateModel.checkForUpdate()
        }
    }

    fun toggleAdBlocking() {
        if (_pending.value) {
            return
        }
        viewModelScope.launch {
            try {
                _pending.value = true
                withContext(Dispatchers.IO) {
                    if (adBlocked.value) {
                        adBlockModel.revert()
                    } else {
                        adBlockModel.apply()
                    }
                }
            } catch (exception: HostErrorException) {
                Timber.w(exception, "Failed to toggle ad blocking.")
                _error.emit(exception.error)
            } finally {
                _pending.value = false
            }
        }
    }

    /**
     * Check the sources for update and retrieve them when at least one is outdated.
     *
     * This is the single entry point behind the home screen update action: checking without
     * retrieving left the user to notice the result and press a second button.
     */
    fun update() {
        if (_pending.value) {
            return
        }
        viewModelScope.launch {
            val application = getApplication<Application>()
            try {
                _pending.value = true
                // Reported from the tap, so leaving the application straight away still shows it.
                ProgressNotifications.report(application, ProgressNotifications.Kind.UPDATE_HOSTS, null)
                withContext(Dispatchers.IO) {
                    val hasUpdate = sourceModel.checkForUpdate { completed, total, _ ->
                        reportSourceProgress(
                            application, completed, total, R.string.notification_update_host_progress_check
                        )
                    }
                    if (!hasUpdate) {
                        return@withContext
                    }
                    sourceModel.retrieveHostsSources { completed, total, _ ->
                        reportSourceProgress(
                            application, completed, total, R.string.notification_update_host_progress_source
                        )
                    }
                    reportApplyingSources(application)
                    adBlockModel.apply()
                }
            } catch (exception: HostErrorException) {
                Timber.w(exception, "Failed to update.")
                _error.emit(exception.error)
            } finally {
                ProgressNotifications.done(application, ProgressNotifications.Kind.UPDATE_HOSTS)
                _pending.value = false
            }
        }
    }

    /**
     * Retrieve the sources unconditionally, without checking them for update first.
     */
    fun sync() {
        if (_pending.value) {
            return
        }
        viewModelScope.launch {
            val application = getApplication<Application>()
            try {
                _pending.value = true
                ProgressNotifications.report(application, ProgressNotifications.Kind.UPDATE_HOSTS, null)
                withContext(Dispatchers.IO) {
                    sourceModel.retrieveHostsSources { completed, total, _ ->
                        reportSourceProgress(
                            application, completed, total, R.string.notification_update_host_progress_source
                        )
                    }
                    reportApplyingSources(application)
                    adBlockModel.apply()
                }
            } catch (exception: HostErrorException) {
                Timber.w(exception, "Failed to sync.")
                _error.emit(exception.error)
            } finally {
                ProgressNotifications.done(application, ProgressNotifications.Kind.UPDATE_HOSTS)
                _pending.value = false
            }
        }
    }

    private fun reportSourceProgress(
        application: Application,
        completed: Int,
        total: Int,
        @StringRes textRes: Int
    ) {
        ProgressNotifications.report(
            application,
            ProgressNotifications.Kind.UPDATE_HOSTS,
            ProgressReporter.percentOf(completed, total),
            application.getString(textRes, (completed + 1).coerceAtMost(total), total)
        )
    }

    private fun reportApplyingSources(application: Application) {
        ProgressNotifications.report(
            application,
            ProgressNotifications.Kind.UPDATE_HOSTS,
            100,
            application.getString(R.string.notification_update_host_progress_apply)
        )
    }

    fun enableAllSources() {
        viewModelScope.launch {
            val enabled = withContext(Dispatchers.IO) {
                sourceModel.enableAllSources()
            }
            if (enabled) {
                sync()
            }
        }
    }

    companion object {
        private const val FLOW_STOP_TIMEOUT_MILLIS = 5_000L
    }
}
