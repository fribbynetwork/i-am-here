package net.fribbynetwork.iamhere.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
fun DestinationScreen(
    vm: TrackerViewModel,
    onBack: () -> Unit,
    onEdit: (Poi?) -> Unit
) {
    val pois by vm.pois.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.destination)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(null) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_point)) }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (prefs.hasDestination) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.selected_destination), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(prefs.destName, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.radius_meters, prefs.destRadius), style = Readout)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { vm.clearDestination() },
                            contentPadding = paddingPulsante,
                            modifier = pulsanteLargo
                        ) { Text(stringResource(R.string.switch_to_free), textAlign = TextAlign.Center) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Text(
                    stringResource(R.string.no_destination_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            if (pois.isEmpty()) {
                Text(
                    stringResource(R.string.no_points_saved),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pois, key = { it.id }) { poi ->
                    PoiRow(
                        poi = poi,
                        selected = prefs.destPoiId == poi.id,
                        defaultRadius = prefs.defaultRadius,
                        onSelect = { vm.chooseDestination(poi) },
                        onEdit = { onEdit(poi) },
                        onDelete = { vm.deletePoi(poi.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PoiRow(
    poi: Poi,
    selected: Boolean,
    defaultRadius: Int,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.selected),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.height(0.dp))
                    }
                    Text(poi.name, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    fmtCoord(poi.lat) + ", " + fmtCoord(poi.lon),
                    style = Readout,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (poi.radius == null)
                        stringResource(R.string.radius_meters_global, defaultRadius)
                    else stringResource(R.string.radius_meters, poi.radius),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
        }
    }
}
