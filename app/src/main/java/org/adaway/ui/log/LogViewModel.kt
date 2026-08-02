package org.adaway.ui.log

import android.app.Application
import androidx.annotation.StringRes
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.AdAwayApplication
import org.adaway.R
import org.adaway.db.AppDatabase
import org.adaway.db.dao.HostEntryDao
import org.adaway.db.dao.HostListItemDao
import org.adaway.db.entity.HostListItem
import org.adaway.db.entity.HostsSource.USER_SOURCE_ID
import org.adaway.db.entity.ListType
import org.adaway.model.adblocking.AdBlockMethod
import org.adaway.model.adblocking.AdBlockModel
import org.adaway.util.ExpressiveToast
import timber.log.Timber

/**
 * A message about the last recording attempt: why it failed, or a limitation of the running one.
 */
data class RecordingMessage(@StringRes val titleRes: Int, val text: String)

class LogViewModel(application: Application) : AndroidViewModel(application) {
    private val adBlockModel: AdBlockModel = (application as AdAwayApplication).adBlockModel
    private val hostListItemDao: HostListItemDao = AppDatabase.getInstance(application).hostsListItemDao()
    private val hostEntryDao: HostEntryDao = AppDatabase.getInstance(application).hostEntryDao()
    private var sort = LogEntrySort.TOP_LEVEL_DOMAIN

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording


    private val _recordingMessage = MutableStateFlow<RecordingMessage?>(null)
    val recordingMessage: StateFlow<RecordingMessage?> = _recordingMessage

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    init {
        refreshRecordingState()
    }

    /**
     * Read the recording state back from the ad block model.
     * It queries the running processes through a privileged shell, so it never runs on the main
     * thread.
     */
    fun refreshRecordingState() {
        viewModelScope.launch {
            _recording.value = withContext(Dispatchers.IO) { adBlockModel.isRecordingLogs }
        }
    }

    fun areBlockedRequestsIgnored(): Boolean = adBlockModel.method == AdBlockMethod.ROOT

    private fun buildRecordingMessage(): RecordingMessage? {
        if (!_recording.value) {
            val failure = adBlockModel.recordingFailure ?: return null
            return RecordingMessage(R.string.dns_recording_error_title, failure)
        }
        val warning = adBlockModel.recordingWarning ?: return null
        return RecordingMessage(R.string.dns_recording_warning_title, warning)
    }

    fun dismissRecordingMessage() {
        _recordingMessage.value = null
    }

    fun clearLogs() {
        adBlockModel.clearLogs()
        _logs.value = emptyList()
        _refreshing.value = false
    }

    fun updateLogs() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val logItems = withContext(Dispatchers.IO) {
                    adBlockModel.logs
                        .parallelStream()
                        .map { log -> LogEntry(log, hostEntryDao.getTypeOfHost(log)) }
                        .sorted(sort.comparator())
                        .toList()
                }
                _logs.value = logItems
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // Reading the captured requests must never bring the application down: it runs on
                // every resume of the screen, whether or not the capture is still running.
                Timber.w(exception, "Failed to read the captured DNS requests.")
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun toggleSort() {
        sortDnsRequests(
            if (sort == LogEntrySort.ALPHABETICAL) {
                LogEntrySort.TOP_LEVEL_DOMAIN
            } else {
                LogEntrySort.ALPHABETICAL
            }
        )
    }

    fun toggleRecording() {
        viewModelScope.launch {
            val enable = !_recording.value
            // Report the state the capture actually ended in rather than the requested one, so a
            // capture that fails to start does not leave the control showing as enabled.
            _recording.value = withContext(Dispatchers.IO) {
                adBlockModel.setRecordingLogs(enable)
                adBlockModel.isRecordingLogs
            }
            _recordingMessage.value = buildRecordingMessage()
        }
    }

    fun addListItem(host: String, type: ListType, redirection: String?) {
        val item = HostListItem().apply {
            this.type = type
            this.host = host
            this.redirection = redirection
            isEnabled = true
            sourceId = USER_SOURCE_ID
        }
        viewModelScope.launch(Dispatchers.IO) {
            hostListItemDao.insert(item)
        }
        updateLogEntryType(host, type)
    }

    fun removeListItem(host: String) {
        viewModelScope.launch(Dispatchers.IO) {
            hostListItemDao.deleteUserFromHost(host)
        }
        updateLogEntryType(host, null)
    }

    private fun updateLogEntryType(host: String, type: ListType?) {
        _logs.value = _logs.value.map { entry ->
            if (entry.host == host) LogEntry(host, type) else entry
        }
    }

    private fun sortDnsRequests(sort: LogEntrySort) {
        this.sort = sort
        _logs.value = _logs.value.sortedWith(sort.comparator())
        ExpressiveToast.makeText(
            getApplication(),
            sort.getName(),
            Toast.LENGTH_SHORT
        ).show()
    }
}
