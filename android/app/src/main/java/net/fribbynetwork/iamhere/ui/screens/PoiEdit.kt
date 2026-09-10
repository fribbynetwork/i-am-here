package net.fribbynetwork.iamhere.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.fribbynetwork.iamhere.data.Poi
import net.fribbynetwork.iamhere.ui.TrackerViewModel
import net.fribbynetwork.iamhere.ui.theme.Readout
import net.fribbynetwork.iamhere.util.fmtCoord
import androidx.compose.ui.res.stringResource
import net.fribbynetwork.iamhere.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoiEditScreen(
    vm: TrackerViewModel,
    existing: Poi?,
    onBack: () -> Unit
) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var radius by remember { mutableStateOf((existing?.radius ?: prefs.defaultRadius).toFloat()) }
    var lat by remember { mutableStateOf(existing?.lat ?: live.lat ?: 44.493772) }
    var lon by remember { mutableStateOf(existing?.lon ?: live.lon ?: 11.343093) }
    var latText by remember { mutableStateOf(fmtCoord(lat)) }
    var lonText by remember { mutableStateOf(fmtCoord(lon)) }
    var manual by remember { mutableStateOf(false) }
    var userTouchedMap by remember { mutableStateOf(false) }

    // Un punto nuovo si apre sulla posizione dell'utente; il ripiego serve
    // solo se il GPS non ha ancora nulla da dare.
    val here by vm.pickerLocation.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (existing == null) vm.refreshPickerLocation() }
    LaunchedEffect(here) {
        val h = here
        if (existing == null && !userTouchedMap && h != null) {
            lat = h.first; lon = h.second
            latText = fmtCoord(lat); lonText = fmtCoord(lon)
        }
    }

    // Risolti qui: dentro una lambda onClick non si puo chiamare stringResource.
    val unnamed = stringResource(R.string.unnamed_point)
    val mapPoint = stringResource(R.string.map_point)

    val style = styleUrlOf(prefs.mapStyleUrl)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.new_point else R.string.edit_point)) },
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            if (!manual) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    MapPicker(
                        styleUrl = style,
                        initialLat = lat,
                        initialLon = lon,
                        radiusMeters = radius.toInt(),
                        onCenterChanged = { la, lo ->
                            lat = la; lon = lo
                            latText = fmtCoord(la); lonText = fmtCoord(lo)
                        },
                        recenterTo = if (existing == null && !userTouchedMap) here else null,
                        onUserTouch = { userTouchedMap = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.drag_map_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(fmtCoord(lat) + ", " + fmtCoord(lon), style = Readout)

            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.arrival_radius, radius.toInt()), style = MaterialTheme.typography.titleSmall)
            Slider(
                value = radius,
                onValueChange = { radius = it },
                valueRange = 50f..2000f,
                steps = 38
            )
            Text(
                stringResource(R.string.radius_floor_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { manual = !manual },
                contentPadding = paddingPulsante,
                modifier = pulsanteLargo
            ) {
                Text(
                    stringResource(if (manual) R.string.back_to_map else R.string.type_coordinates),
                    textAlign = TextAlign.Center
                )
            }

            if (manual) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = {
                            latText = it
                            it.replace(',', '.').toDoubleOrNull()?.let { v -> if (v in -90.0..90.0) lat = v }
                        },
                        label = { Text(stringResource(R.string.latitude)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lonText,
                        onValueChange = {
                            lonText = it
                            it.replace(',', '.').toDoubleOrNull()?.let { v -> if (v in -180.0..180.0) lon = v }
                        },
                        label = { Text(stringResource(R.string.longitude)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val currentPos = here ?: live.lat?.let { la -> live.lon?.let { lo -> la to lo } }
            if (currentPos != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        userTouchedMap = true
                        lat = currentPos.first; lon = currentPos.second
                        latText = fmtCoord(lat); lonText = fmtCoord(lon)
                        vm.refreshPickerLocation()
                    },
                    contentPadding = paddingPulsante,
                    modifier = pulsanteLargo
                ) { Text(stringResource(R.string.use_current_position), textAlign = TextAlign.Center) }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val n = name.ifBlank { unnamed }
                    if (existing == null) vm.addPoi(n, lat, lon, radius.toInt())
                    else vm.updatePoi(existing.copy(name = n, lat = lat, lon = lon, radius = radius.toInt()))
                    onBack()
                },
                contentPadding = paddingPulsante,
                modifier = pulsanteLargo
            ) {
                Text(
                    stringResource(if (existing == null) R.string.save_point else R.string.save_changes),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    vm.setAdHocDestination(lat, lon, radius.toInt(), name.ifBlank { mapPoint })
                    onBack()
                },
                contentPadding = paddingPulsante,
                modifier = pulsanteLargo
            ) {
                Text(
                    stringResource(R.string.use_without_saving),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}
