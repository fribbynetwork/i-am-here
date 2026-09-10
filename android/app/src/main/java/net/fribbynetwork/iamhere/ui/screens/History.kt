package net.fribbynetwork.iamhere.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.fribbynetwork.iamhere.ui.TrackerViewModel
import net.fribbynetwork.iamhere.ui.theme.Readout
import net.fribbynetwork.iamhere.util.fmtClock
import net.fribbynetwork.iamhere.util.fmtCoord
import net.fribbynetwork.iamhere.util.fmtDateTime
import androidx.compose.ui.res.stringResource
import net.fribbynetwork.iamhere.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: TrackerViewModel, onBack: () -> Unit) {
    val samples by vm.samples.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (trips.isNotEmpty()) {
                val t = trips.first()
                Text(stringResource(R.string.last_trip), style = MaterialTheme.typography.titleSmall)
                val where = t.destName?.let { " " + stringResource(R.string.heading_to, it) }
                    ?: " (" + stringResource(R.string.trip_free) + ")"
                val statusText = t.endReason?.let { " · " + stringResource(R.string.trip_closed, it) }
                    ?: " · " + stringResource(R.string.trip_running)
                Text(
                    fmtDateTime(t.startedAt) + where + statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = { vm.clearHistory() },
                contentPadding = paddingPulsante,
                modifier = pulsanteLargo
            ) { Text(stringResource(R.string.clear_history), textAlign = TextAlign.Center) }
            Spacer(Modifier.height(12.dp))

            if (samples.isEmpty()) {
                Text(
                    stringResource(R.string.no_sends_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn {
                items(samples, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${s.event}  ${fmtClock(s.fixTime * 1000L)}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(fmtCoord(s.lat) + ", " + fmtCoord(s.lon), style = Readout)
                            Text(
                                when {
                                    s.sentAt != null -> stringResource(R.string.sent_at, fmtClock(s.sentAt))
                                    s.lastError != null ->
                                        stringResource(R.string.queued_attempts, s.attempts, s.lastError)
                                    else -> stringResource(R.string.queued)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (s.sentAt == null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
