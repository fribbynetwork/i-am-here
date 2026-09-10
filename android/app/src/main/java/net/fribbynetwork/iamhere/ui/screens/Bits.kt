package net.fribbynetwork.iamhere.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Misura unica per i pulsanti a piena larghezza. Materiale 3 centra gia
 * il contenuto: e la larghezza che va imposta, altrimenti il pulsante si
 * stringe sul testo e nella colonna resta allineato a sinistra.
 */
val pulsanteLargo: Modifier
    get() = Modifier
        .fillMaxWidth()
        .heightIn(min = 52.dp)

val paddingPulsante = PaddingValues(horizontal = 20.dp, vertical = 14.dp)

@Composable
fun Section(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
    Spacer(Modifier.height(12.dp))
}

/**
 * Testo esplicativo dentro una sezione. Le spiegazioni lunghe stanno qui
 * e non sotto al titolo: il sottotitolo deve dire in una riga di cosa si
 * occupa la sezione, non spiegarne il funzionamento.
 */
@Composable
fun Nota(testo: String) {
    Text(
        testo,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
fun SwitchRow(label: String, help: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (help != null) {
                Text(
                    help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(0.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
    Spacer(Modifier.height(8.dp))
}

/** Casella di spunta con l'etichetta cliccabile insieme al quadratino. */
@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Il gruppo usa un'altezza intrinseca: tutti i pulsanti prendono quella
 * dell'etichetta piu alta, quindi restano uguali fra loro e nessun testo
 * viene tagliato. Togliendo la spunta si guadagna la larghezza che serve
 * a scritte come "POST JSON".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, text) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                // Senza questo il pulsante prende l'altezza del proprio
                // testo: quello che va a capo diventa piu alto degli altri.
                modifier = Modifier.fillMaxHeight(),
                icon = {},
                label = {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
fun SliderRow(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    suffix: String,
    help: String? = null,
    onChange: (Int) -> Unit
) {
    Text("$label: $value$suffix", style = MaterialTheme.typography.labelLarge)
    Slider(
        value = value.toFloat().coerceIn(range.start, range.endInclusive),
        onValueChange = { onChange(it.toInt()) },
        valueRange = range,
        steps = steps
    )
    if (help != null) {
        Text(
            help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(12.dp))
}

/**
 * Sezione che si apre e si chiude. Le impostazioni avanzate stanno qui
 * dentro: chi usa solo gli SMS non deve scorrerle ogni volta.
 */
@Composable
fun SectionExpandable(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    var aperta by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { aperta = !aperta },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (subtitle != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    if (aperta) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
            if (aperta) {
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

/**
 * Campo per un segreto: resta sempre mascherato, senza pulsante per
 * rivelarlo. Sotto compare l'impronta, le stesse sei cifre che mostra il
 * server: se coincidono hai incollato il valore giusto, e non c'e stato
 * bisogno di mostrarlo a nessuno.
 */
@Composable
fun SecretRow(
    label: String,
    value: String,
    help: String? = null,
    impronta: String = "",
    etichettaImpronta: String = "",
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        supportingText = if (help != null) ({ Text(help) }) else null,
        modifier = Modifier.fillMaxWidth()
    )
    if (impronta.isNotEmpty()) {
        Text(
            "$etichettaImpronta $impronta",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp)
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
fun TextRow(
    label: String,
    value: String,
    help: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    numeric: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number)
        else KeyboardOptions.Default,
        supportingText = if (help != null) ({ Text(help) }) else null,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
}
