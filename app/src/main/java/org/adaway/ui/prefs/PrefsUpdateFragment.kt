package org.adaway.ui.prefs

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.model.source.SourceUpdateService
import org.adaway.ui.compose.ExpressiveAsymmetricShape1
import org.adaway.ui.compose.ExpressiveAsymmetricShape2
import org.adaway.ui.compose.ExpressivePage
import org.adaway.ui.compose.safeClickable

@Composable
internal fun PrefsUpdateScreen(
    notificationsDisabled: Boolean,
    checkAppStartup: Boolean,
    checkAppDaily: Boolean,
    includeBetaReleases: Boolean,
    includeBetaEnabled: Boolean,
    checkHostsStartup: Boolean,
    checkHostsDaily: Boolean,
    automaticUpdateDaily: Boolean,
    updateOnlyOnWifi: Boolean,
    updateIntervalHours: Int,
    onOpenNotifications: () -> Unit,
    onCheckAppStartupChanged: (Boolean) -> Unit,
    onCheckAppDailyChanged: (Boolean) -> Unit,
    onIncludeBetaChanged: (Boolean) -> Unit,
    onCheckHostsStartupChanged: (Boolean) -> Unit,
    onCheckHostsDailyChanged: (Boolean) -> Unit,
    onAutomaticUpdateDailyChanged: (Boolean) -> Unit,
    onUpdateOnlyWifiChanged: (Boolean) -> Unit,
    onUpdateIntervalChanged: (Int) -> Unit
) {
    var intervalPickerVisible by remember { mutableStateOf(false) }
    ExpressivePage {
        if (notificationsDisabled) {
            PreferenceSection(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                shape = ExpressiveAsymmetricShape1
            ) {
                PreferenceRow(
                    iconRes = R.drawable.notifications_off_24,
                    titleRes = R.string.pref_update_enable_notifications,
                    summary = stringResource(R.string.pref_update_enable_notifications_summary),
                    onClick = onOpenNotifications,
                    iconTint = MaterialTheme.colorScheme.onErrorContainer,
                    titleColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_update_app_category)
        PreferenceSection(shape = ExpressiveAsymmetricShape2) {
            PreferenceToggleRow(
                iconRes = R.drawable.ic_sync_24dp,
                titleRes = R.string.pref_update_check_app_startup,
                checked = checkAppStartup,
                onCheckedChange = onCheckAppStartupChanged
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_sync_24dp,
                titleRes = R.string.pref_update_check_app_daily,
                checked = checkAppDaily,
                onCheckedChange = onCheckAppDailyChanged
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_outline_rule_24,
                titleRes = R.string.pref_update_include_beta_releases,
                checked = includeBetaReleases,
                enabled = includeBetaEnabled,
                onCheckedChange = onIncludeBetaChanged
            )
        }

        PreferenceCategoryHeader(titleRes = R.string.pref_update_hosts_category)
        PreferenceSection(shape = ExpressiveAsymmetricShape1) {
            PreferenceToggleRow(
                iconRes = R.drawable.ic_sync_24dp,
                titleRes = R.string.pref_update_check,
                checked = checkHostsStartup,
                onCheckedChange = onCheckHostsStartupChanged
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_sync_24dp,
                titleRes = R.string.pref_update_check_hosts_daily,
                checked = checkHostsDaily,
                onCheckedChange = onCheckHostsDailyChanged
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_playlist_add_24dp,
                titleRes = R.string.pref_update_sync_on_update,
                checked = automaticUpdateDaily,
                enabled = checkHostsDaily,
                onCheckedChange = onAutomaticUpdateDailyChanged
            )
            PreferenceDivider()
            PreferenceRow(
                iconRes = R.drawable.ic_sync_24dp,
                titleRes = R.string.pref_update_interval,
                summary = stringResource(updateIntervalLabel(updateIntervalHours)),
                enabled = checkHostsDaily,
                onClick = { intervalPickerVisible = true }
            )
            PreferenceDivider()
            PreferenceToggleRow(
                iconRes = R.drawable.ic_vpn_key_24dp,
                titleRes = R.string.pref_update_sync_unmetered_only,
                checked = updateOnlyOnWifi,
                enabled = checkHostsDaily,
                onCheckedChange = onUpdateOnlyWifiChanged
            )
        }

        if (intervalPickerVisible) {
            AlertDialog(
                onDismissRequest = { intervalPickerVisible = false },
                title = { Text(text = stringResource(R.string.pref_update_interval)) },
                text = {
                    Column {
                        SourceUpdateService.UPDATE_INTERVALS_HOURS.forEach { hours ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .safeClickable {
                                        onUpdateIntervalChanged(hours)
                                        intervalPickerVisible = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = hours == updateIntervalHours,
                                    onClick = {
                                        onUpdateIntervalChanged(hours)
                                        intervalPickerVisible = false
                                    }
                                )
                                Text(
                                    text = stringResource(updateIntervalLabel(hours)),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { intervalPickerVisible = false }) {
                        Text(text = stringResource(android.R.string.cancel))
                    }
                }
            )
        }
        Spacer(modifier = Modifier.size(32.dp))
    }
}

@StringRes
private fun updateIntervalLabel(hours: Int): Int = when (hours) {
    12 -> R.string.pref_update_interval_12h
    24 -> R.string.pref_update_interval_24h
    48 -> R.string.pref_update_interval_2d
    168 -> R.string.pref_update_interval_1w
    else -> R.string.pref_update_interval_6h
}
