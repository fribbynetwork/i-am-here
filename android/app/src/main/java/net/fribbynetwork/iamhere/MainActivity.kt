package net.fribbynetwork.iamhere

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.fribbynetwork.iamhere.data.Poi
import net.fribbynetwork.iamhere.ui.TrackerViewModel
import net.fribbynetwork.iamhere.ui.screens.DestinationScreen
import net.fribbynetwork.iamhere.ui.screens.DiagnosticsScreen
import net.fribbynetwork.iamhere.ui.screens.HistoryScreen
import net.fribbynetwork.iamhere.ui.screens.HomeScreen
import net.fribbynetwork.iamhere.ui.screens.PoiEditScreen
import net.fribbynetwork.iamhere.ui.screens.SettingsScreen
import net.fribbynetwork.iamhere.ui.theme.TrackerTheme
import net.fribbynetwork.iamhere.util.withLocale

sealed interface Route {
    data object Home : Route
    data object Destination : Route
    data object Settings : Route
    data object History : Route
    data object Diagnostics : Route
    data class EditPoi(val poi: Poi?) : Route
}

class MainActivity : ComponentActivity() {

    private val backgroundLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            // La posizione in background non si puo chiedere insieme a quella
            // in primo piano: Android rifiuta la richiesta combinata. Va in un
            // secondo giro, e solo dopo che la prima e stata concessa.
            val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                granted(Manifest.permission.ACCESS_FINE_LOCATION)
            if (fine && !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestBasePermissions()
        setContent { AppRoot() }
    }

    /** Tutto quello che serve, in un unico giro di richieste all'avvio. */
    private fun requestBasePermissions() {
        val wanted = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = wanted.filterNot { granted(it) }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else if (!granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
}

@Composable
private fun AppRoot() {
    val vm: TrackerViewModel = viewModel()

    /*
     * Il controllo delle versioni parte qui, all'apertura, e solo se
     * l'utente lo ha acceso e sono passati i giorni che ha scelto. Non
     * serve un lavoro pianificato: chi apre l'app e il momento giusto
     * per dirglielo, e chi non la apre non ha bisogno di saperlo.
     */
    LaunchedEffect(Unit) { vm.controllaAggiornamenti() }
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var stack by remember { mutableStateOf(listOf<Route>(Route.Home)) }

    fun go(r: Route) { stack = stack + r }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }

    // Il tasto indietro di sistema deve fare quello che fa la freccia in
    // alto a sinistra. Attivo solo se c'e dove tornare: dalla schermata
    // principale continua a chiudere l'app, come ci si aspetta.
    BackHandler(enabled = stack.size > 1) { back() }

    // Tutte le schermate leggono le stringhe da questo contesto, quindi
    // cambiare lingua nelle impostazioni ridisegna subito l'interfaccia
    // senza toccare le impostazioni di sistema.
    val base = LocalContext.current
    val localized = remember(prefs.language) { base.withLocale(prefs.language) }

    TrackerTheme(prefs.theme) {
      CompositionLocalProvider(LocalContext provides localized) {
        when (val current = stack.last()) {
            is Route.Home -> HomeScreen(
                vm = vm,
                onDestination = { go(Route.Destination) },
                onSettings = { go(Route.Settings) },
                onHistory = { go(Route.History) },
                onDiagnostics = { go(Route.Diagnostics) }
            )
            is Route.Destination -> DestinationScreen(
                vm = vm,
                onBack = { back() },
                onEdit = { go(Route.EditPoi(it)) }
            )
            is Route.EditPoi -> PoiEditScreen(
                vm = vm,
                existing = current.poi,
                onBack = { back() }
            )
            is Route.Settings -> SettingsScreen(vm = vm, onBack = { back() })
            is Route.History -> HistoryScreen(vm = vm, onBack = { back() })
            is Route.Diagnostics -> DiagnosticsScreen(onBack = { back() })
        }
      }
    }
}
