package com.tdbe.playonconnect.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tdbe.playonconnect.MainActivity
import com.tdbe.playonconnect.R
import com.tdbe.playonconnect.data.AppSettings
import com.tdbe.playonconnect.data.PendingDelete
import com.tdbe.playonconnect.data.RuleStore
import com.tdbe.playonconnect.engine.PlaybackState
import com.tdbe.playonconnect.service.PlaybackService
import com.tdbe.playonconnect.util.BluetoothUtil
import com.tdbe.playonconnect.util.WebApps
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import java.util.UUID

private const val GITHUB_URL = "https://github.com/tdbe/Play-On-Connect"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isEditing by rememberSaveable { mutableStateOf(false) }
    var editingRuleId by rememberSaveable { mutableStateOf<String?>(null) }

    val rules by RuleStore.rules.collectAsState()
    val playing by PlaybackState.current.collectAsState()
    val pendingEntries by PendingDelete.entries.collectAsState()
    val resumeTick by MainActivity.resumeCount.collectAsState()

    val btPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notifPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val showBluetoothRow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val bluetoothGranted = !showBluetoothRow || BluetoothUtil.hasConnectPermission(context)
    val showNotificationRow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val notificationGranted = !showNotificationRow || NotificationManagerCompat.from(context).areNotificationsEnabled()
    val overlayGranted = remember(resumeTick) { Settings.canDrawOverlays(context) }

    val displayRules = rules.filterNot { it.pendingDelete }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
            bottomBar = {
                if (!isEditing) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column {
                            Text(
                                GITHUB_URL,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { WebApps.open(context, GITHUB_URL, null) }
                                    .padding(vertical = 8.dp),
                            )
                            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!isEditing) {
                    FloatingActionButton(onClick = { editingRuleId = null; isEditing = true }) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_add_rule))
                    }
                }
            }
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                if (isEditing) {
                    RuleEditScreen(
                        ruleId = editingRuleId,
                        rules = rules,
                        onDone = { isEditing = false },
                        onRequestDelete = { id -> scope.launch { PendingDelete.begin(id) } },
                    )
                } else {
                    RuleListScreen(
                        rules = displayRules,
                        playingRuleId = playing?.ruleId,
                        headerContent = {
                            Column {
                                val maxBannerHeight = LocalConfiguration.current.screenHeightDp.dp * 0.3f
                                Image(
                                    painter = painterResource(R.drawable.tv_banner),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = maxBannerHeight),
                                )
                                Text(
                                    stringResource(R.string.header_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                PermissionsSection(
                                    showBluetoothRow = showBluetoothRow,
                                    bluetoothGranted = bluetoothGranted,
                                    onGrantBluetooth = {
                                        if (bluetoothGranted) openAppDetails(context)
                                        else btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                    },
                                    showNotificationRow = showNotificationRow,
                                    notificationGranted = notificationGranted,
                                    onGrantNotification = {
                                        if (notificationGranted) openAppDetails(context)
                                        else notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    overlayGranted = overlayGranted,
                                    onGrantOverlay = {
                                        context.startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}"),
                                            )
                                        )
                                    },
                                )
                                ParallelSettingCard()
                            }
                        },
                        onEdit = { rule -> editingRuleId = rule.id; isEditing = true },
                        onToggle = { rule, enabled ->
                            scope.launch {
                                RuleStore.setEnabled(rule.id, enabled)
                                if (enabled && !PlaybackService.isRunning) PlaybackService.start(context)
                            }
                        },
                        onStop = { PlaybackService.stopPlayback(context) },
                        onDelete = { rule -> scope.launch { PendingDelete.begin(rule.id) } },
                        onCopy = { rule ->
                            scope.launch {
                                RuleStore.insertAfter(
                                    rule.id,
                                    rule.copy(id = UUID.randomUUID().toString(), pendingDelete = false),
                                )
                            }
                        },
                    )
                }
            }
        }

        if (pendingEntries.isNotEmpty()) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                pendingEntries.forEach { entry ->
                    Surface(
                        tonalElevation = 3.dp,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp),
                        ) {
                            Text(stringResource(R.string.rule_deleted_label))
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { scope.launch { PendingDelete.undo(entry.id) } }) {
                                Text(stringResource(R.string.undo_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openAppDetails(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
    )
}

@Composable
private fun ParallelSettingCard() {
    var enabled by remember { mutableStateOf(AppSettings.playInParallel) }
    fun set(value: Boolean) {
        enabled = value
        AppSettings.playInParallel = value
    }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { set(!enabled) }
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = enabled, onCheckedChange = ::set)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.parallel_title), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.parallel_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val PREFS_UI = "ui_state"
private const val PREF_KEY_PERMISSIONS_EXPANDED = "permissions_expanded"

@Composable
private fun PermissionsSection(
    showBluetoothRow: Boolean,
    bluetoothGranted: Boolean,
    onGrantBluetooth: () -> Unit,
    showNotificationRow: Boolean,
    notificationGranted: Boolean,
    onGrantNotification: () -> Unit,
    overlayGranted: Boolean,
    onGrantOverlay: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE) }
    var expanded by remember { mutableStateOf(prefs.getBoolean(PREF_KEY_PERMISSIONS_EXPANDED, true)) }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        expanded = !expanded
                        prefs.edit().putBoolean(PREF_KEY_PERMISSIONS_EXPANDED, expanded).apply()
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.permissions_section_title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showBluetoothRow) {
                        PermissionButton(
                            granted = bluetoothGranted,
                            grantLabel = stringResource(R.string.perm_bluetooth_grant),
                            grantedLabel = stringResource(R.string.perm_bluetooth_granted),
                            onGrant = onGrantBluetooth,
                        )
                    }
                    if (showNotificationRow) {
                        PermissionButton(
                            granted = notificationGranted,
                            grantLabel = stringResource(R.string.perm_notifications_grant),
                            grantedLabel = stringResource(R.string.perm_notifications_granted),
                            onGrant = onGrantNotification,
                        )
                    }
                    PermissionButton(
                        granted = overlayGranted,
                        grantLabel = stringResource(R.string.perm_overlay_grant),
                        grantedLabel = stringResource(R.string.perm_overlay_granted),
                        onGrant = onGrantOverlay,
                        note = stringResource(R.string.perm_overlay_note),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionButton(
    granted: Boolean,
    grantLabel: String,
    grantedLabel: String,
    onGrant: () -> Unit,
    note: String? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth(),
            colors = if (granted) {
                ButtonDefaults.outlinedButtonColors().copy(
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
            border = BorderStroke(
                1.dp,
                if (granted) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outline,
            ),
        ) {
            Text(if (granted) grantedLabel else grantLabel)
        }
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
