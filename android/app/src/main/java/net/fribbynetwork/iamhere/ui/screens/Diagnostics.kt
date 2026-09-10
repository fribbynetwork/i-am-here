package net.fribbynetwork.iamhere.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import net.fribbynetwork.iamhere.R
import net.fribbynetwork.iamhere.util.appSettingsIntent
import net.fribbynetwork.iamhere.util.batteryOptimizationIntent
import net.fribbynetwork.iamhere.util.batteryOptimizationRequestIntent
import net.fribbynetwork.iamhere.util.isIgnoringBatteryOptimizations
import androidx.compose.ui.res.stringResource

private data class Check(
    val label: String,
    val ok: Boolean,
    val help: String,
    val fix: (() -> Unit)?,
    /** Se manca, la condivisione non funziona. Altrimenti e solo un miglioramento. */
    val essenziale: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Il ricontrollo serve solo finche qualcosa manca. Il caso normale e
    // "esco, concedo, rientro", e per quello basta l'evento di ripresa:
    // e istantaneo e non costa nulla. Il ciclo lento copre il caso in cui
    // il permesso viene concesso senza mai lasciare questa schermata.
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    // Lettura esplicita: e questa che fa ricalcolare i controlli qui sotto.
    @Suppress("UNUSED_EXPRESSION") tick

    fun granted(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val gpsOn = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)

    val openApp = { ctx.startActivity(ctx.appSettingsIntent()) }
    val openBattery = {
        // Prima la finestra secca; se la ROM non ce l'ha, l'elenco di sistema.
        runCatching { ctx.startActivity(ctx.batteryOptimizationRequestIntent()) }
            .onFailure { runCatching { ctx.startActivity(ctx.batteryOptimizationIntent()) } }
        Unit
    }

    val checks = buildList {
        add(
            Check(
                stringResource(R.string.check_fine_location),
                granted(Manifest.permission.ACCESS_FINE_LOCATION),
                stringResource(R.string.check_fine_location_help), openApp
            )
        )
        add(
            Check(
                stringResource(R.string.check_background_location),
                granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                stringResource(R.string.check_background_location_help), openApp
            )
        )
        add(
            Check(
                stringResource(R.string.check_gps_on), gpsOn,
                stringResource(R.string.check_gps_on_help), null
            )
        )
        if (Build.VERSION.SDK_INT >= 33) {
            add(
                Check(
                    stringResource(R.string.check_notifications),
                    granted(Manifest.permission.POST_NOTIFICATIONS),
                    stringResource(R.string.check_notifications_help), openApp
                )
            )
        }
        add(
            Check(
                stringResource(R.string.check_sms), granted(Manifest.permission.SEND_SMS),
                stringResource(R.string.check_sms_help), openApp, essenziale = false
            )
        )
        add(
            Check(
                stringResource(R.string.check_battery), ctx.isIgnoringBatteryOptimizations(),
                stringResource(R.string.check_battery_help), openBattery, essenziale = false
            )
        )
    }

    val allOk = checks.all { it.ok }
    LaunchedEffect(allOk) {
        // Due secondi: impercettibile all'occhio e a batteria, mentre un
        // secondo farebbe ricomporre la lista di continuo per niente.
        while (!allOk) {
            delay(2000)
            tick++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics)) },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(
                    when {
                        allOk -> R.string.diagnostics_all_ok
                        checks.any { !it.ok && it.essenziale } -> R.string.diagnostics_intro
                        else -> R.string.diagnostics_only_optional
                    }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            checks.forEach { c ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (c.ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = when {
                                c.ok -> MaterialTheme.colorScheme.primary
                                c.essenziale -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                        )
                        Spacer(Modifier.height(0.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(c.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                c.help,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!c.ok && c.fix != null) {
                            OutlinedButton(onClick = { c.fix?.invoke() }) { Text(stringResource(R.string.open_settings)) }
                        }
                    }
                }
            }

            if (!granted(Manifest.permission.SEND_SMS)) {
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.restricted_settings),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.restricted_settings_help),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.autostart), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.autostart_help),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
