package net.fribbynetwork.iamhere.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.fribbynetwork.iamhere.R
import net.fribbynetwork.iamhere.data.Backup
import net.fribbynetwork.iamhere.data.HttpMethod
import net.fribbynetwork.iamhere.data.LangMode
import net.fribbynetwork.iamhere.data.Prefs
import net.fribbynetwork.iamhere.data.SendMode
import net.fribbynetwork.iamhere.net.Sms
import net.fribbynetwork.iamhere.net.Templates
import net.fribbynetwork.iamhere.ui.TrackerViewModel
import net.fribbynetwork.iamhere.ui.theme.Readout
import net.fribbynetwork.iamhere.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: TrackerViewModel, onBack: () -> Unit) {
    val loaded by vm.prefsOrNull.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val smsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    var draft by remember { mutableStateOf<Prefs?>(null) }
    /** Messaggio dell'ultima operazione. Dichiarato qui perche lo usano
     *  anche i selettori di file poco piu sotto. */
    var dialog by remember { mutableStateOf<String?>(null) }
    /** Il facsimile appena generato: si mostra in un campo a parte invece
     *  di sovrascrivere un messaggio che l'utente aveva gia scritto. */
    var facsimile by remember { mutableStateOf<String?>(null) }
    val appunti = LocalClipboardManager.current
    /** Ultimo segnaposto toccato, per mostrargli accanto la conferma. */
    var segnaCopiato by remember { mutableStateOf<String?>(null) }

    // --- salvataggio e ripristino
    var salvaImpostazioni by remember { mutableStateOf(true) }
    var salvaPunti by remember { mutableStateOf(true) }
    /** La password confermata, quella che i selettori di file useranno. */
    var passwordBackup by remember { mutableStateOf("") }
    var chiedeSalva by remember { mutableStateOf(false) }
    var chiedeRipristina by remember { mutableStateOf(false) }
    var pw1 by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var erroreBackup by remember { mutableStateOf<String?>(null) }
    /** Contenuto letto e in attesa di una risposta sui punti esistenti. */
    var daImportare by remember { mutableStateOf<Backup.Contenuto?>(null) }

    val pois by vm.pois.collectAsStateWithLifecycle()
    val ctxAvviso: (Int) -> String = { context.getString(it) }

    val creaFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            vm.esporta(uri, passwordBackup, salvaImpostazioni, salvaPunti) { ok ->
                dialog = context.getString(
                    if (ok) R.string.backup_done_export else R.string.backup_failed
                )
            }
        }
    }

    val apriFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.leggiBackup(uri, passwordBackup) { esito ->
                when (esito) {
                    is Backup.Esito.PasswordSbagliata ->
                        dialog = context.getString(R.string.backup_wrong_password)
                    is Backup.Esito.FileNonValido ->
                        dialog = context.getString(R.string.backup_bad_file)
                    is Backup.Esito.Ok -> {
                        // Si chiede solo se c'e davvero qualcosa da perdere.
                        if (esito.contenuto.punti.isNotEmpty() && pois.isNotEmpty()) {
                            daImportare = esito.contenuto
                        } else {
                            vm.applicaBackup(esito.contenuto, sostituisciPunti = false) { n ->
                                draft = null   // le impostazioni sono cambiate: si rilegge
                                dialog = context.getString(R.string.backup_done_import, n)
                            }
                        }
                    }
                }
            }
        }
    }
    if (loaded != null && draft == null) draft = loaded

    val whenLabel = stringResource(R.string.when_label)
    val modeOptions = listOf(
        SendMode.START_END to stringResource(R.string.mode_start_end),
        SendMode.ALWAYS to stringResource(R.string.mode_always)
    )
    val inherits = stringResource(R.string.inherits_from_start)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { pad ->
        val p = draft
        if (p == null) {
            Column(Modifier.padding(pad).fillMaxSize().padding(32.dp)) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        fun edit(block: (Prefs) -> Prefs) {
            val next = block(p)
            draft = next
            vm.save(next)
        }

        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {

            Section(
                stringResource(R.string.how_to_transmit),
                stringResource(R.string.how_to_transmit_help)
            ) {
                Column {
                    SwitchRow(stringResource(R.string.send_to_endpoint), null, p.endpointEnabled) {
                        edit { s -> s.copy(endpointEnabled = it) }
                    }
                    if (p.endpointEnabled) {
                        ChoiceRow(whenLabel, modeOptions, p.endpointMode) {
                            edit { s -> s.copy(endpointMode = it) }
                        }
                    }
                    SwitchRow(stringResource(R.string.send_by_sms), null, p.smsEnabled) { on ->
                        edit { s -> s.copy(smsEnabled = on) }
                        // Chiediamo il permesso qui, non al primo invio fallito.
                        if (on && ContextCompat.checkSelfPermission(
                                context, Manifest.permission.SEND_SMS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            smsPermission.launch(
                                arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE)
                            )
                        }
                    }
                    if (p.smsEnabled) {
                        ChoiceRow(whenLabel, modeOptions, p.smsMode) {
                            edit { s -> s.copy(smsMode = it) }
                        }
                    }
                }
            }

            if (p.endpointEnabled) {
                Section(
                    stringResource(R.string.endpoint),
                    stringResource(R.string.endpoint_help)
                ) {
                    Column {
                        ChoiceRow(
                            stringResource(R.string.method),
                            listOf(false to "GET", true to "POST"),
                            p.method != HttpMethod.GET
                        ) { isPost ->
                            edit { s ->
                                s.copy(method = if (isPost) HttpMethod.POST_FORM else HttpMethod.GET)
                            }
                        }

                        if (p.method != HttpMethod.GET) {
                            ChoiceRow(
                                stringResource(R.string.body_format),
                                listOf(
                                    HttpMethod.POST_FORM to stringResource(R.string.body_form),
                                    HttpMethod.POST_JSON to stringResource(R.string.body_json)
                                ),
                                p.method
                            ) { edit { s -> s.copy(method = it) } }
                            Text(
                                stringResource(
                                    if (p.method == HttpMethod.POST_JSON) R.string.body_json_help
                                    else R.string.body_form_help
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        TextRow(
                            stringResource(R.string.url_start), p.urlStart,
                            singleLine = false, minLines = 3
                        ) { edit { s -> s.copy(urlStart = it) } }

                        TextRow(
                            stringResource(R.string.url_track), p.urlTrack,
                            help = inherits, singleLine = false, minLines = 2
                        ) { edit { s -> s.copy(urlTrack = it) } }

                        TextRow(
                            stringResource(R.string.url_end), p.urlEnd,
                            help = inherits, singleLine = false, minLines = 2
                        ) { edit { s -> s.copy(urlEnd = it) } }

                        TextRow(
                            stringResource(R.string.http_headers), p.headers,
                            help = stringResource(R.string.http_headers_help),
                            singleLine = false, minLines = 2
                        ) { edit { s -> s.copy(headers = it) } }

                        OutlinedButton(
                            onClick = { vm.testSend("start") { dialog = it } },
                            contentPadding = paddingPulsante,
                            modifier = pulsanteLargo
                        ) { Text(stringResource(R.string.test_send), textAlign = TextAlign.Center) }
                    }
                }

                SectionExpandable(
                    stringResource(R.string.connection),
                    stringResource(R.string.connection_sub)
                ) {
                    Column {
                        Nota(stringResource(R.string.connection_help))
                        SwitchRow(
                            stringResource(R.string.allow_cleartext),
                            stringResource(R.string.allow_cleartext_help),
                            p.allowCleartext
                        ) { edit { s -> s.copy(allowCleartext = it) } }

                        SwitchRow(
                            stringResource(R.string.allow_insecure_tls),
                            stringResource(R.string.allow_insecure_tls_help),
                            p.allowInsecureTls
                        ) { edit { s -> s.copy(allowInsecureTls = it) } }

                        // L'avviso compare solo quando una delle due protezioni
                        // e disattivata: un allarme sempre acceso viene ignorato.
                        if (p.allowCleartext || p.allowInsecureTls) {
                            Spacer(Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        stringResource(R.string.security_warning),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        stringResource(
                                            if (p.allowInsecureTls) R.string.security_warning_tls
                                            else R.string.security_warning_http
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (p.smsEnabled) {
                val sims = remember { activeSims(context) }
                val simDefault = stringResource(R.string.sim_default)
                SectionExpandable(stringResource(R.string.sms)) {
                    Column {
                        Nota(stringResource(R.string.sms_help))
                        TextRow(
                            stringResource(R.string.recipients), p.smsRecipients,
                            help = stringResource(R.string.recipients_help)
                        ) { edit { s -> s.copy(smsRecipients = it) } }

                        if (sims.size > 1) {
                            ChoiceRow(
                                stringResource(R.string.sim),
                                listOf(-1 to simDefault) + sims,
                                p.smsSubscriptionId
                            ) { edit { s -> s.copy(smsSubscriptionId = it) } }
                        }

                        TextRow(
                            stringResource(R.string.sms_start), p.smsStart,
                            singleLine = false, minLines = 2
                        ) { edit { s -> s.copy(smsStart = it) } }
                        ContaSms(p, p.smsStart)

                        TextRow(
                            stringResource(R.string.sms_track), p.smsTrack,
                            help = inherits, singleLine = false, minLines = 2
                        ) { edit { s -> s.copy(smsTrack = it) } }
                        if (p.smsTrack.isNotBlank()) ContaSms(p, p.smsTrack)

                        TextRow(
                            stringResource(R.string.sms_end), p.smsEnd,
                            singleLine = false, minLines = 2
                        ) { edit { s -> s.copy(smsEnd = it) } }
                        ContaSms(p, p.smsEnd)

                        Text(
                            stringResource(R.string.sms_prefab),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.sms_prefab_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))

                        // Non scrivono in un campo: mostrano il testo e lo
                        // copiano, cosi lo incolli dove ti serve senza
                        // sovrascrivere quello che avevi gia.
                        val proponi = { testo: String ->
                            facsimile = testo
                            appunti.setText(AnnotatedString(testo))
                        }

                        OutlinedButton(
                            onClick = { proponi(PREFAB_OSM) },
                            contentPadding = paddingPulsante,
                            modifier = pulsanteLargo
                        ) { Text(stringResource(R.string.prefab_osm), textAlign = TextAlign.Center) }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { proponi(prefabIamHere(p.urlStart)) },
                            contentPadding = paddingPulsante,
                            modifier = pulsanteLargo
                        ) { Text(stringResource(R.string.prefab_iamhere), textAlign = TextAlign.Center) }

                        facsimile?.let { testo ->
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = testo,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.prefab_result)) },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                stringResource(R.string.copied),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        SliderRow(
                            stringResource(R.string.sms_min_interval), p.smsMinIntervalSec / 60,
                            1f..30f, 28, stringResource(R.string.minutes_short),
                            stringResource(R.string.sms_min_interval_help)
                        ) { edit { s -> s.copy(smsMinIntervalSec = it * 60) } }

                        OutlinedButton(
                            onClick = { vm.testSms("start") { dialog = it } },
                            contentPadding = paddingPulsante,
                            modifier = pulsanteLargo
                        ) { Text(stringResource(R.string.test_sms), textAlign = TextAlign.Center) }
                    }
                }
            }

            SectionExpandable(stringResource(R.string.cadence)) {
                Column {
                    Nota(stringResource(R.string.cadence_help))
                    SliderRow(
                        stringResource(R.string.interval), p.intervalSec,
                        10f..600f, 58, stringResource(R.string.seconds_short)
                    ) { edit { s -> s.copy(intervalSec = it) } }

                    SliderRow(
                        stringResource(R.string.min_distance), p.minDistanceM,
                        0f..1000f, 39, stringResource(R.string.meters_suffix)
                    ) { edit { s -> s.copy(minDistanceM = it) } }

                    SwitchRow(
                        stringResource(R.string.adaptive_cadence),
                        stringResource(R.string.adaptive_cadence_help),
                        p.adaptive
                    ) { edit { s -> s.copy(adaptive = it) } }
                }
            }

            SectionExpandable(stringResource(R.string.trip)) {
                Column {
                    SliderRow(
                        stringResource(R.string.default_radius), p.defaultRadius,
                        50f..1000f, 37, stringResource(R.string.meters_suffix),
                        stringResource(R.string.default_radius_help)
                    ) { edit { s -> s.copy(defaultRadius = it) } }

                    SliderRow(
                        stringResource(R.string.max_duration), p.maxTripMinutes,
                        0f..720f, 47, stringResource(R.string.minutes_short),
                        stringResource(
                            if (p.maxTripMinutes == 0) R.string.max_duration_off
                            else R.string.max_duration_on
                        )
                    ) { edit { s -> s.copy(maxTripMinutes = it) } }

                    SliderRow(
                        stringResource(R.string.battery_stop), p.batteryStopPercent,
                        0f..50f, 49, stringResource(R.string.percent_suffix),
                        stringResource(
                            if (p.batteryStopPercent == 0) R.string.battery_stop_off
                            else R.string.battery_stop_on
                        )
                    ) { edit { s -> s.copy(batteryStopPercent = it) } }

                    SliderRow(
                        stringResource(R.string.coord_decimals), p.coordDecimals,
                        4f..8f, 3, "",
                        stringResource(R.string.coord_decimals_help)
                    ) { edit { s -> s.copy(coordDecimals = it) } }
                }
            }

            SectionExpandable(stringResource(R.string.map)) {
                Column {
                    Nota(stringResource(R.string.map_help))
                    TextRow(
                        stringResource(R.string.custom_style_url), p.mapStyleUrl,
                        help = stringResource(R.string.custom_style_help)
                    ) { edit { s -> s.copy(mapStyleUrl = it) } }
                }
            }

            SectionExpandable(stringResource(R.string.appearance)) {
                Column {
                    ChoiceRow(
                        stringResource(R.string.theme),
                        listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.theme_light),
                            ThemeMode.DARK to stringResource(R.string.theme_dark)
                        ),
                        p.theme
                    ) { edit { s -> s.copy(theme = it) } }

                    ChoiceRow(
                        stringResource(R.string.language),
                        listOf(
                            LangMode.SYSTEM to stringResource(R.string.language_system),
                            LangMode.EN to stringResource(R.string.language_en),
                            LangMode.IT to stringResource(R.string.language_it)
                        ),
                        p.language
                    ) { edit { s -> s.copy(language = it) } }
                }
            }

            SectionExpandable(
                stringResource(R.string.advanced),
                stringResource(R.string.advanced_sub)
            ) {
                Column {
                    Nota(stringResource(R.string.advanced_help))
                    Text(
                        stringResource(R.string.iamhere_server),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.iamhere_server_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    SecretRow(
                        stringResource(R.string.token_secret), p.tokenSecret,
                        help = stringResource(R.string.token_secret_help),
                        impronta = Templates.impronta(p.tokenSecret),
                        etichettaImpronta = stringResource(R.string.fingerprint)
                    ) { edit { s -> s.copy(tokenSecret = it) } }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    Text(stringResource(R.string.yourls), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.yourls_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))

                    SwitchRow(stringResource(R.string.yourls_use), null, p.yourlsEnabled) {
                        edit { s -> s.copy(yourlsEnabled = it) }
                    }

                    if (p.yourlsEnabled) {
                        TextRow(
                            stringResource(R.string.yourls_api), p.yourlsApi,
                            help = stringResource(R.string.yourls_api_help)
                        ) { edit { s -> s.copy(yourlsApi = it) } }

                        SecretRow(
                            stringResource(R.string.yourls_token), p.yourlsToken,
                            help = stringResource(R.string.yourls_token_help)
                        ) { edit { s -> s.copy(yourlsToken = it) } }

                        TextRow(
                            stringResource(R.string.yourls_template), p.yourlsTemplate,
                            help = stringResource(R.string.yourls_template_help),
                            singleLine = false, minLines = 2
                        ) { edit { s -> s.copy(yourlsTemplate = it) } }

                        ChoiceRow(
                            stringResource(R.string.yourls_when),
                            listOf(
                                false to stringResource(R.string.yourls_once),
                                true to stringResource(R.string.yourls_every)
                            ),
                            p.yourlsOgniMessaggio
                        ) { edit { s -> s.copy(yourlsOgniMessaggio = it) } }

                        OutlinedButton(
                            onClick = { vm.testYourls { dialog = it } },
                            contentPadding = paddingPulsante,
                            modifier = pulsanteLargo
                        ) { Text(stringResource(R.string.yourls_test), textAlign = TextAlign.Center) }
                    }
                }
            }

            // ------------------------------------------- salvataggio
            SectionExpandable(
                stringResource(R.string.backup),
                stringResource(R.string.backup_sub)
            ) {
                Column {
                    Nota(stringResource(R.string.backup_help))

                    CheckRow(stringResource(R.string.backup_settings), salvaImpostazioni) {
                        salvaImpostazioni = it
                    }
                    CheckRow(stringResource(R.string.backup_points), salvaPunti) {
                        salvaPunti = it
                    }
                    Spacer(Modifier.height(8.dp))

                    Spacer(Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = {
                            if (!salvaImpostazioni && !salvaPunti) {
                                dialog = ctxAvviso(R.string.backup_nothing)
                            } else {
                                pw1 = ""; pw2 = ""; erroreBackup = null
                                chiedeSalva = true
                            }
                        },
                        contentPadding = paddingPulsante,
                        modifier = pulsanteLargo
                    ) { Text(stringResource(R.string.backup_export), textAlign = TextAlign.Center) }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            pw1 = ""; erroreBackup = null
                            chiedeRipristina = true
                        },
                        contentPadding = paddingPulsante,
                        modifier = pulsanteLargo
                    ) { Text(stringResource(R.string.backup_import), textAlign = TextAlign.Center) }
                }
            }

            SectionExpandable(stringResource(R.string.placeholders)) {
                Column {
                    Nota(stringResource(R.string.placeholders_help))
                    Nota(stringResource(R.string.placeholders_tap))

                    Templates.CATALOG.forEach { (key, descRes) ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    appunti.setText(AnnotatedString("{$key}"))
                                    segnaCopiato = key
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("{$key}", style = Readout)
                                if (segnaCopiato == key) {
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.copied),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            Text(
                                stringResource(descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // --- password per salvare: chiesta due volte, perche sbagliarla qui
    // significa ritrovarsi con un file che non si apre piu.
    if (chiedeSalva) {
        AlertDialog(
            onDismissRequest = { chiedeSalva = false },
            title = { Text(stringResource(R.string.backup_export_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.backup_password_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pw1,
                        onValueChange = { pw1 = it; erroreBackup = null },
                        label = { Text(stringResource(R.string.backup_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pw2,
                        onValueChange = { pw2 = it; erroreBackup = null },
                        label = { Text(stringResource(R.string.backup_password_again)) },
                        singleLine = true,
                        isError = erroreBackup != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    erroreBackup?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pw1.length < 6 -> erroreBackup = ctxAvviso(R.string.backup_need_password)
                        pw1 != pw2 -> erroreBackup = ctxAvviso(R.string.backup_password_mismatch)
                        else -> {
                            passwordBackup = pw1
                            chiedeSalva = false
                            creaFile.launch("iamhere-backup.json")
                        }
                    }
                }) { Text(stringResource(R.string.backup_choose_where)) }
            },
            dismissButton = {
                TextButton(onClick = { chiedeSalva = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // --- password per ripristinare: una sola, e poi si sceglie il file
    if (chiedeRipristina) {
        AlertDialog(
            onDismissRequest = { chiedeRipristina = false },
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.backup_import_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pw1,
                        onValueChange = { pw1 = it; erroreBackup = null },
                        label = { Text(stringResource(R.string.backup_password)) },
                        singleLine = true,
                        isError = erroreBackup != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    erroreBackup?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pw1.isEmpty()) {
                        erroreBackup = ctxAvviso(R.string.backup_need_password)
                    } else {
                        passwordBackup = pw1
                        chiedeRipristina = false
                        apriFile.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                }) { Text(stringResource(R.string.backup_choose_file)) }
            },
            dismissButton = {
                TextButton(onClick = { chiedeRipristina = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    daImportare?.let { c ->
        AlertDialog(
            onDismissRequest = { daImportare = null },
            title = { Text(stringResource(R.string.backup_points_exist)) },
            text = { Text(stringResource(R.string.backup_points_exist_text, c.punti.size, pois.size)) },
            confirmButton = {
                TextButton(onClick = {
                    daImportare = null
                    vm.applicaBackup(c, sostituisciPunti = true) { n ->
                        draft = null
                        dialog = context.getString(R.string.backup_done_import, n)
                    }
                }) { Text(stringResource(R.string.backup_replace)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    daImportare = null
                    vm.applicaBackup(c, sostituisciPunti = false) { n ->
                        draft = null
                        dialog = context.getString(R.string.backup_done_import, n)
                    }
                }) { Text(stringResource(R.string.backup_keep)) }
            }
        )
    }

    if (dialog != null) {
        AlertDialog(
            onDismissRequest = { dialog = null },
            confirmButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.close)) }
            },
            title = { Text(stringResource(R.string.test_result)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(dialog!!, style = Readout)
                }
            }
        )
    }
}

/**
 * Quanti caratteri e quanti messaggi. Il conteggio si fa sul testo
 * gia composto con una posizione di esempio, non sul template: i
 * segnaposto occupano meno di quello che diventeranno.
 */
@Composable
private fun ContaSms(p: Prefs, template: String) {
    if (template.isBlank()) return
    val reso = Templates.render(
        template,
        Templates.valuesOf(
            Templates.demoSample(), p.coordDecimals,
            segreto = p.tokenSecret, yourls = "https://esempio.tld/xyz"
        ),
        Templates.Mode.RAW
    )
    val c = Sms.conta(reso)
    Text(
        stringResource(R.string.sms_count, c.caratteri, c.limite, c.messaggi) +
            if (c.unicode) "  ·  " + stringResource(R.string.sms_unicode) else "",
        style = MaterialTheme.typography.bodySmall,
        color = if (c.messaggi > 1) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
    )
}

/** Facsimile con un link a OpenStreetMap: funziona senza nessun server. */
private const val PREFAB_OSM =
    "{evento} https://www.openstreetmap.org/?mlat={lat}&mlon={lon}#map=16/{lat}/{lon}"

/**
 * Facsimile per il server di I am here. L'indirizzo si ricava
 * dall'endpoint gia configurato, cosi non va riscritto a mano.
 */
private fun prefabIamHere(urlEndpoint: String): String {
    val base = runCatching {
        val u = java.net.URI(urlEndpoint.substringBefore('{'))
        if (u.scheme != null && u.host != null) "${u.scheme}://${u.host}" else null
    }.getOrNull() ?: "https://TUO-SERVER"
    return "{evento} $base/?id={timestamp}&auth={auth}"
}

/** SIM attive, per i telefoni dual SIM. */
@android.annotation.SuppressLint("MissingPermission")
private fun activeSims(context: android.content.Context): List<Pair<Int, String>> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
        != PackageManager.PERMISSION_GRANTED
    ) return emptyList()
    return try {
        val sm = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        sm.activeSubscriptionInfoList?.map {
            it.subscriptionId to (it.displayName?.toString() ?: "SIM ${it.simSlotIndex + 1}")
        } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
