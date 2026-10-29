package com.tdbe.playonconnect.ui

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tdbe.playonconnect.R
import com.tdbe.playonconnect.data.Action
import com.tdbe.playonconnect.data.Rule
import com.tdbe.playonconnect.data.RuleStore
import com.tdbe.playonconnect.data.Trigger
import com.tdbe.playonconnect.service.PlaybackService
import com.tdbe.playonconnect.util.AudioInfo
import com.tdbe.playonconnect.util.BluetoothUtil
import com.tdbe.playonconnect.util.TimeSpec
import com.tdbe.playonconnect.util.WebApps
import com.tdbe.playonconnect.util.formatMinutes
import com.tdbe.playonconnect.util.sanitizeDecimalInput
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.floor

private enum class ActionType { PLAY_SOUND, OPEN_URL }

@Composable
fun RuleEditScreen(ruleId: String?, rules: List<Rule>, onDone: () -> Unit, onRequestDelete: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = rules.find { it.id == ruleId }

    var actionType by rememberSaveable(existing?.id) {
        mutableStateOf(if (existing?.action is Action.OpenUrl) ActionType.OPEN_URL else ActionType.PLAY_SOUND)
    }

    var triggerIsTime by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.trigger is Trigger.TimeEvent)
    }
    var timeSpec by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.trigger as? Trigger.TimeEvent)?.spec ?: "")
    }

    // Play-sound fields
    var soundUris by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.action as? Action.PlaySound)?.effectiveUris ?: emptyList())
    }
    var soundNames by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.action as? Action.PlaySound)?.effectiveNames ?: emptyList())
    }
    var loop by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.action as? Action.PlaySound)?.loop ?: false)
    }
    var soundMaxText by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.action as? Action.PlaySound)?.maxMinutes?.toString() ?: "")
    }
    var fadeText by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.action as? Action.PlaySound)?.fadeSeconds?.toString() ?: "")
    }

    // Open-url fields
    var url by rememberSaveable(existing?.id) { mutableStateOf((existing?.action as? Action.OpenUrl)?.url ?: "") }
    var useDedicated by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.action as? Action.OpenUrl)?.useDedicatedApp ?: false)
    }
    var browserPackage by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.action as? Action.OpenUrl)?.browserPackage)
    }
    var appPackage by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.action as? Action.OpenUrl)?.appPackage)
    }
    var urlMaxText by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.action as? Action.OpenUrl)?.maxMinutes?.toString() ?: "")
    }

    // Trigger fields
    var deviceAddress by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.trigger as? Trigger.BluetoothDeviceConnected)?.deviceAddress)
    }
    var deviceName by rememberSaveable(existing?.id) {
        mutableStateOf((existing?.trigger as? Trigger.BluetoothDeviceConnected)?.deviceName)
    }

    var showDevicePicker by rememberSaveable { mutableStateOf(false) }
    var showBrowserPicker by rememberSaveable { mutableStateOf(false) }
    var showAppPicker by rememberSaveable { mutableStateOf(false) }

    // When not looping, stop-after/fade must not exceed the actual content length.
    fun clampToPlaylistLength() {
        if (loop) return
        val totalSec = AudioInfo.totalDurationSec(context, soundUris) ?: return
        val maxAllowedMinutes = floor(totalSec / 60.0 * 100.0) / 100.0
        val currentMax = sanitizeDecimalInput(soundMaxText).toDoubleOrNull()
        if (currentMax != null && currentMax > maxAllowedMinutes) {
            soundMaxText = formatMinutes(maxAllowedMinutes)
        }
        val effectiveSeconds =
            sanitizeDecimalInput(soundMaxText).toDoubleOrNull()?.let { (it * 60).toLong() } ?: totalSec
        val fade = fadeText.trim().toIntOrNull()
        if (fade != null && fade > effectiveSeconds) fadeText = effectiveSeconds.toString()
    }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            soundUris = uris.map { it.toString() }
            soundNames = uris.map {
                BluetoothUtil.fileDisplayName(context, it) ?: it.lastPathSegment ?: it.toString()
            }
            clampToPlaylistLength()
        }
    }

    val hasDefaultBrowser = remember { WebApps.hasDefaultBrowser(context) }
    val dedicatedPackage = remember(url) { if (url.isBlank()) null else WebApps.dedicatedAppPackage(context, url) }
    val browserOptions = remember { WebApps.browserApps(context) }
    val urlAppOptions = remember(url) { WebApps.appsForUrl(context, url) }

    val browserPickerNeeded = !useDedicated && !hasDefaultBrowser
    val appPickerNeeded = useDedicated && dedicatedPackage == null

    val soundMax = sanitizeDecimalInput(soundMaxText).toDoubleOrNull()?.takeIf { it > 0 }
    val fadeRaw = fadeText.trim().toIntOrNull()
    val urlMax = sanitizeDecimalInput(urlMaxText).toDoubleOrNull()?.takeIf { it > 0 }

    // Fade is meaningless (and hidden) for endless looping without a stop time.
    val fadeVisible = !(loop && soundMax == null)

    val timeSpecValid = TimeSpec.parse(timeSpec) != null

    val valid = (if (triggerIsTime) timeSpecValid else deviceAddress != null) && when (actionType) {
        ActionType.PLAY_SOUND -> soundUris.isNotEmpty()

        ActionType.OPEN_URL ->
            url.isNotBlank() &&
                (!browserPickerNeeded || browserPackage != null) &&
                (!appPickerNeeded || appPackage != null)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_cancel))
            }
            Text(
                stringResource(if (existing == null) R.string.title_add_rule else R.string.title_edit_rule),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            if (existing != null) {
                IconButton(onClick = {
                    onRequestDelete(existing.id)
                    onDone()
                }) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete))
                }
            }
            TextButton(
                enabled = valid,
                onClick = {
                    val action: Action = when (actionType) {
                        ActionType.PLAY_SOUND -> {
                            val maxSeconds = soundMax?.let { (it * 60).toLong() }
                            val clampedFade = if (maxSeconds != null && fadeRaw != null) {
                                minOf(fadeRaw.toLong(), maxSeconds).toInt().takeIf { it >= 1 }
                            } else {
                                null
                            }
                            Action.PlaySound(
                                uri = soundUris.first(),
                                uris = soundUris.takeIf { it.size > 1 },
                                displayName = soundNames.firstOrNull(),
                                displayNames = soundNames.takeIf { it.size > 1 },
                                loop = loop,
                                maxMinutes = soundMax,
                                fadeSeconds = clampedFade,
                            )
                        }

                        ActionType.OPEN_URL -> Action.OpenUrl(
                            url = url.trim(),
                            useDedicatedApp = useDedicated,
                            browserPackage = browserPackage,
                            appPackage = appPackage,
                            maxMinutes = urlMax,
                        )
                    }
                    val trigger: Trigger = if (triggerIsTime) {
                        Trigger.TimeEvent(timeSpec.trim())
                    } else {
                        Trigger.BluetoothDeviceConnected(deviceAddress!!, deviceName)
                    }
                    val rule = Rule(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        enabled = existing?.enabled ?: true,
                        trigger = trigger,
                        action = action,
                    )
                    scope.launch {
                        RuleStore.upsert(rule)
                        if (rule.enabled && !PlaybackService.isRunning) PlaybackService.start(context)
                        onDone()
                    }
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.section_trigger).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        FilterChip(
                            selected = !triggerIsTime,
                            onClick = { triggerIsTime = false },
                            label = { Text(stringResource(R.string.trigger_type_bluetooth)) },
                        )
                        Text(stringResource(R.string.or_label), style = MaterialTheme.typography.bodySmall)
                        FilterChip(
                            selected = triggerIsTime,
                            onClick = {
                                triggerIsTime = true
                                if (timeSpec.isBlank()) timeSpec = TimeSpec.defaultSpec()
                            },
                            label = { Text(stringResource(R.string.trigger_type_time_event)) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!triggerIsTime) {
                        OutlinedButton(onClick = { showDevicePicker = true }) {
                            Text(deviceName ?: deviceAddress ?: stringResource(R.string.no_device_selected))
                        }
                    } else {
                        OutlinedTextField(
                            value = timeSpec,
                            onValueChange = { timeSpec = it },
                            label = { Text(stringResource(R.string.time_spec_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(
                                if (timeSpecValid) R.string.time_spec_hint else R.string.time_spec_invalid
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (timeSpecValid) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.section_action).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.Start) {
                        FilterChip(
                            selected = actionType == ActionType.PLAY_SOUND,
                            onClick = { actionType = ActionType.PLAY_SOUND },
                            label = { Text(stringResource(R.string.action_type_play_sound)) },
                        )
                        Text(stringResource(R.string.or_label), style = MaterialTheme.typography.bodySmall)
                        FilterChip(
                            selected = actionType == ActionType.OPEN_URL,
                            onClick = { actionType = ActionType.OPEN_URL },
                            label = { Text(stringResource(R.string.action_type_open_url)) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    if (actionType == ActionType.PLAY_SOUND) {
                        OutlinedButton(onClick = {
                            soundPicker.launch(arrayOf("audio/*", "application/ogg", "application/x-ogg"))
                        }) {
                            val label = when {
                                soundNames.isEmpty() -> stringResource(R.string.choose_sound)
                                soundNames.size == 1 -> soundNames[0]
                                else -> stringResource(R.string.summary_files_count, soundNames.size)
                            }
                            Text(label)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.loop_switch), Modifier.weight(1f))
                            Switch(
                                checked = loop,
                                onCheckedChange = { value ->
                                    loop = value
                                    if (!value) clampToPlaylistLength()
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = soundMaxText,
                            onValueChange = { raw ->
                                val cleaned = sanitizeDecimalInput(raw)
                                soundMaxText = cleaned
                                // Cap the fade to the new stop-after value.
                                val seconds = cleaned.toDoubleOrNull()?.let { (it * 60).toLong() }
                                if (seconds != null && seconds >= 1) {
                                    val fade = fadeText.trim().toIntOrNull()
                                    if (fade != null && fade > seconds) {
                                        fadeText = seconds.toString()
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.max_minutes_label)) },
                            placeholder = { Text(stringResource(R.string.max_minutes_placeholder)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (fadeVisible) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = fadeText,
                                onValueChange = { raw ->
                                    var value = raw.filter { c -> c.isDigit() }.toIntOrNull()
                                    val maxSeconds = soundMax?.let { (it * 60).toLong() }
                                    if (value != null && maxSeconds != null && maxSeconds >= 1 && value > maxSeconds) {
                                        value = maxSeconds.toInt()
                                    }
                                    fadeText = value?.toString() ?: ""
                                },
                                label = { Text(stringResource(R.string.fade_seconds_label)) },
                                placeholder = { Text(stringResource(R.string.fade_seconds_placeholder)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(stringResource(R.string.url_label)) },
                            placeholder = { Text(stringResource(R.string.url_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.clickable { useDedicated = false },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = !useDedicated, onClick = { useDedicated = false })
                            Text(stringResource(R.string.url_mode_browser))
                        }
                        Row(
                            Modifier.clickable { useDedicated = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = useDedicated, onClick = { useDedicated = true })
                            Text(stringResource(R.string.url_mode_dedicated))
                        }
                        Spacer(Modifier.height(8.dp))
                        if (browserPickerNeeded) {
                            AppPickerField(
                                label = stringResource(R.string.pick_browser_app),
                                selectedPackage = browserPackage,
                                requiredHint = stringResource(R.string.required_no_default_browser),
                                onClick = { showBrowserPicker = true },
                            )
                        }
                        if (appPickerNeeded) {
                            AppPickerField(
                                label = stringResource(R.string.pick_app_to_use),
                                selectedPackage = appPackage,
                                requiredHint = stringResource(R.string.required_no_dedicated_app),
                                onClick = { showAppPicker = true },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = urlMaxText,
                            onValueChange = { raw -> urlMaxText = sanitizeDecimalInput(raw) },
                            label = { Text(stringResource(R.string.max_minutes_label)) },
                            placeholder = { Text(stringResource(R.string.max_minutes_placeholder)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(LocalConfiguration.current.screenHeightDp.dp * 0.5f))
        }
    }

    if (showDevicePicker) {
        BluetoothDevicePickerDialog(
            onDismiss = { showDevicePicker = false },
            onPick = { address, name ->
                deviceAddress = address
                deviceName = name
                showDevicePicker = false
            },
        )
    }
    if (showBrowserPicker) {
        AppListPickerDialog(
            title = stringResource(R.string.pick_browser_app),
            options = browserOptions,
            onDismiss = { showBrowserPicker = false },
            onSelect = { browserPackage = it.packageName; showBrowserPicker = false },
        )
    }
    if (showAppPicker) {
        AppListPickerDialog(
            title = stringResource(R.string.pick_app_to_use),
            options = urlAppOptions,
            onDismiss = { showAppPicker = false },
            onSelect = { appPackage = it.packageName; showAppPicker = false },
        )
    }
}

@Composable
private fun AppPickerField(
    label: String,
    selectedPackage: String?,
    requiredHint: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onClick) {
            Text(selectedPackage?.let { WebApps.appLabel(context, it) } ?: stringResource(R.string.choose_app))
        }
        if (selectedPackage == null) {
            Spacer(Modifier.height(4.dp))
            Text(requiredHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AppListPickerDialog(
    title: String,
    options: List<WebApps.AppOption>,
    onDismiss: () -> Unit,
    onSelect: (WebApps.AppOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                if (options.isEmpty()) {
                    Text(stringResource(R.string.no_apps_found))
                } else {
                    LazyColumn {
                        items(options, key = { it.packageName }) { option ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(option) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(option.label, fontWeight = FontWeight.Medium)
                                    Text(
                                        option.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun BluetoothDevicePickerDialog(
    onDismiss: () -> Unit,
    onPick: (address: String, name: String) -> Unit,
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }
    val hasPermission = remember(refreshKey) { BluetoothUtil.hasConnectPermission(context) }
    val devices = remember(refreshKey) {
        BluetoothUtil.bondedDevices(context).sortedBy { BluetoothUtil.displayName(it).lowercase() }
    }
    val connected by BluetoothUtil.connectedAddresses(context).collectAsState(initial = emptySet())
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshKey++ }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pick_device)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                when {
                    BluetoothUtil.getAdapter(context) == null ->
                        Text(stringResource(R.string.no_bluetooth))

                    !hasPermission -> {
                        Text(stringResource(R.string.banner_bt_permission))
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                            Text(stringResource(R.string.action_grant))
                        }
                    }

                    devices.isEmpty() ->
                        Text(stringResource(R.string.no_paired_devices))

                    else -> LazyColumn {
                        items(devices, key = { it.address }) { device ->
                            val name = remember(device) { BluetoothUtil.displayName(device) }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(device.address, name) }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.Medium)
                                    Text(
                                        device.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (connected.contains(device.address)) {
                                    Text(
                                        stringResource(R.string.badge_connected),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { refreshKey++ }) { Text(stringResource(R.string.action_refresh)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
