package com.tdbe.playonconnect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tdbe.playonconnect.R
import com.tdbe.playonconnect.data.Action
import com.tdbe.playonconnect.data.Rule
import com.tdbe.playonconnect.data.Trigger
import com.tdbe.playonconnect.util.BluetoothUtil
import com.tdbe.playonconnect.util.formatMinutes
import android.net.Uri

@Composable
fun RuleListScreen(
    rules: List<Rule>,
    playingRuleId: String?,
    headerContent: @Composable () -> Unit,
    onEdit: (Rule) -> Unit,
    onToggle: (Rule, Boolean) -> Unit,
    onStop: () -> Unit,
    onDelete: (Rule) -> Unit,
    onCopy: (Rule) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { headerContent() }
        if (rules.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.empty_rules),
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    isPlaying = playingRuleId == rule.id,
                    onEdit = { onEdit(rule) },
                    onToggle = { enabled -> onToggle(rule, enabled) },
                    onStop = onStop,
                    onDelete = { onDelete(rule) },
                    onCopy = { onCopy(rule) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    isPlaying: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val trigger = rule.trigger as? Trigger.BluetoothDeviceConnected

    Card(modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        trigger?.deviceName ?: trigger?.deviceAddress ?: stringResource(R.string.no_device_selected),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    when (val action = rule.action) {
                        is Action.PlaySound -> PlaySoundSummary(action)
                        is Action.OpenUrl -> OpenUrlSummary(action)
                    }
                }
                IconButton(onClick = onCopy) {
                    Icon(painterResource(R.drawable.ic_copy), contentDescription = stringResource(R.string.action_copy))
                }
                IconButton(onClick = onDelete) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete))
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            if (isPlaying) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStop) {
                    Text(stringResource(R.string.action_stop))
                }
            }
        }
    }
}

@Composable
private fun PlaySoundSummary(action: Action.PlaySound) {
    val context = LocalContext.current
    val names = action.effectiveNames
    val label = when {
        names.size == 1 -> names[0]
        names.size > 1 -> stringResource(R.string.summary_files_count, action.effectiveUris.size)
        else -> stringResource(R.string.label_sound)
    }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(4.dp))
    val summary = buildString {
        append(stringResource(if (action.loop) R.string.summary_loops else R.string.summary_plays_once))
        action.maxMinutes?.let {
            append(" · ")
            append(stringResource(R.string.summary_stops_after, formatMinutes(it)))
        }
        action.fadeSeconds?.let {
            append(" · ")
            append(stringResource(R.string.summary_fades_over, it))
        }
    }
    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val fileMissing = remember(action.uri, action.uris) {
        action.effectiveUris.any { !BluetoothUtil.canOpen(context, Uri.parse(it)) }
    }
    if (fileMissing) {
        Text(
            stringResource(R.string.file_missing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun OpenUrlSummary(action: Action.OpenUrl) {
    Text(
        action.url,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    val summary = buildString {
        append(stringResource(if (action.useDedicatedApp) R.string.summary_mode_dedicated else R.string.summary_mode_browser))
        action.maxMinutes?.let {
            append(" · ")
            append(stringResource(R.string.summary_stops_after, formatMinutes(it)))
        }
    }
    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
