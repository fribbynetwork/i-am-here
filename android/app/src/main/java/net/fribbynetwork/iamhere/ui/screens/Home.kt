package net.fribbynetwork.iamhere.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import net.fribbynetwork.iamhere.ui.TrackerViewModel
import net.fribbynetwork.iamhere.ui.theme.Readout
import net.fribbynetwork.iamhere.ui.theme.ReadoutBig
import net.fribbynetwork.iamhere.util.fmtCoord
import net.fribbynetwork.iamhere.util.fmtElapsed
import net.fribbynetwork.iamhere.util.fmtMeters
import net.fribbynetwork.iamhere.util.fmtSpeed
import androidx.compose.ui.res.stringResource
import net.fribbynetwork.iamhere.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: TrackerViewModel,
    onDestination: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val live by vm.live.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val pending by vm.pendingCount.collectAsStateWithLifecycle()

    // Un avviso in home quando manca un permesso essenziale: senza, l'unico
    // segnale sarebbe un invio che fallisce in silenzio.
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) permTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    @Suppress("UNUSED_EXPRESSION") permTick

    fun has(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    val missing = !has(Manifest.permission.ACCESS_FINE_LOCATION) ||
        !has(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ||
        (Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS)) ||
        (prefs.smsEnabled && !has(Manifest.permission.SEND_SMS))

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live.running) {
        while (live.running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LogoApp()
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.history))
                    }
                    IconButton(onClick = onDiagnostics) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.diagnostics))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (missing) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.permissions_missing),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.permissions_missing_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onDiagnostics,
                            contentPadding = paddingPulsante,
                            modifier = pulsanteLargo
                        ) { Text(stringResource(R.string.open_diagnostics), textAlign = TextAlign.Center) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            StatusPanel(
                running = live.running,
                elapsed = if (live.running) fmtElapsed(now - live.startedAt) else null,
                destName = live.destName,
                distToDest = live.distToDest,
                tripId = live.tripId
            )

            Spacer(Modifier.height(16.dp))

            // heightIn invece di height: il pulsante cresce con il testo
            // invece di tagliarlo quando la traduzione e piu lunga.
            Button(
                onClick = { vm.start() },
                enabled = !live.running,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.start_sharing), style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = { vm.stop() },
                enabled = live.running,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.stop_sharing))
            }

            Spacer(Modifier.height(10.dp))

            FilledTonalButton(
                onClick = onDestination,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Icon(Icons.Default.Place, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (prefs.hasDestination)
                        stringResource(R.string.destination_named, prefs.destName)
                    else stringResource(R.string.choose_destination),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))

            FixCard(
                lat = live.lat, lon = live.lon, acc = live.accuracy,
                speed = live.speed, alt = live.altitude,
                satUsed = live.satUsed, satTotal = live.satTotal,
                decimals = prefs.coordDecimals
            )

            Spacer(Modifier.height(12.dp))

            TransmissionCard(
                sentCount = live.sentCount,
                pending = pending,
                lastResult = live.lastResult,
                lastResultOk = live.lastResultOk,
                smsAttivi = prefs.smsEnabled,
                smsCount = live.smsCount,
                smsPending = live.smsPending,
                smsResult = live.smsResult,
                smsResultOk = live.smsResultOk,
                onRetry = { vm.retryQueue() }
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Il logo dell'app, montato dai due livelli dell'icona adattiva.
 * Il fattore 1.5 mostra i 72dp centrali dei 108 della tela, cioe
 * esattamente la porzione che il launcher lascia vedere dopo la maschera.
 */
@Composable
private fun LogoApp() {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.5f)
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.5f)
        )
    }
}

/**
 * L'unico elemento che si prende una liberta cromatica: diventa arancione
 * quando l'app trasmette. E l'informazione che serve capire al volo.
 */
@Composable
private fun StatusPanel(
    running: Boolean,
    elapsed: String?,
    destName: String?,
    distToDest: Double?,
    tripId: Long
) {
    val bg by animateColorAsState(
        if (running) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
        label = "statusBg"
    )
    val fg = if (running) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (running) fg else Color.Transparent)
                )
                if (running) Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (running) R.string.sharing_active else R.string.sharing_stopped),
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(elapsed ?: "--:--", style = ReadoutBig, color = fg)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    !running && destName == null -> stringResource(R.string.no_trip)
                    destName != null && distToDest != null ->
                        stringResource(R.string.heading_to_with_distance, destName, fmtMeters(distToDest))
                    destName != null -> stringResource(R.string.heading_to, destName)
                    else -> stringResource(R.string.free_sharing)
                },
                color = fg,
                style = MaterialTheme.typography.bodyMedium
            )
            if (running) {
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.trip_id, tripId), style = Readout, color = fg)
            }
        }
    }
}

@Composable
private fun FixCard(
    lat: Double?, lon: Double?, acc: Float?, speed: Float?, alt: Double?,
    satUsed: Int?, satTotal: Int?, decimals: Int
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.last_fix), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            if (lat == null || lon == null) {
                Text(stringResource(R.string.waiting_for_gps), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(fmtCoord(lat, decimals) + ", " + fmtCoord(lon, decimals), style = Readout)
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val dash = stringResource(R.string.dash)
                    Metric(
                        stringResource(R.string.accuracy),
                        acc?.let { stringResource(R.string.meters_short, it.toInt()) } ?: dash
                    )
                    Metric(stringResource(R.string.speed), fmtSpeed(speed))
                    Metric(
                        stringResource(R.string.altitude),
                        alt?.let { stringResource(R.string.meters_short, it.toInt()) } ?: dash
                    )
                    Metric(
                        stringResource(R.string.satellites),
                        if (satTotal != null) "${satUsed ?: 0}/$satTotal" else dash
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(value, style = Readout)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransmissionCard(
    sentCount: Int,
    pending: Int,
    lastResult: String?,
    lastResultOk: Boolean?,
    smsAttivi: Boolean,
    smsCount: Int,
    smsPending: Int,
    smsResult: String?,
    smsResultOk: Boolean?,
    onRetry: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.transmissions), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(stringResource(R.string.sent), sentCount.toString())
                Metric(stringResource(R.string.queued), pending.toString())
                // Il conteggio degli SMS compare solo se quel canale e
                // acceso: altrimenti sarebbe uno zero senza significato.
                if (smsAttivi) {
                    Metric(stringResource(R.string.sms_sent_count), smsCount.toString())
                }
            }
            if (smsAttivi && smsPending > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.sms_queued_count) + ": " + smsPending,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            // Riga separata per gli SMS: con una sola, condivisa con
            // l'endpoint, l'errore spariva in pochi secondi.
            if (smsResult != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    smsResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (smsResultOk == false) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (lastResult != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    lastResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lastResultOk == false) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (pending > 0) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRetry,
                    contentPadding = paddingPulsante,
                    modifier = pulsanteLargo
                ) { Text(stringResource(R.string.retry_now), textAlign = TextAlign.Center) }
            }
        }
    }
}
