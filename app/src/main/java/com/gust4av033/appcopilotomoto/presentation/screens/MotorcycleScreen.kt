package com.gust4av033.appcopilotomoto.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.*
import com.gust4av033.appcopilotomoto.presentation.CopilotActions
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import com.gust4av033.appcopilotomoto.presentation.components.*
import com.gust4av033.appcopilotomoto.presentation.theme.Glass
import java.time.LocalDate
import java.util.Locale

fun LazyListScope.MotorcycleScreen(state: CopilotUiState, actions: CopilotActions) {
    val moto = state.motorcycle
    val editable = !state.busy && moto.loaded && !moto.working && !moto.error
    item("motorcycle_status") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SupportingText(stringResource(R.string.motorcycle_local))
            if (state.busy) SupportingText(stringResource(R.string.motorcycle_locked))
            if (moto.working || !moto.loaded && !moto.error) SupportingText(stringResource(R.string.motorcycle_loading))
            moto.messageRes?.let { Text(stringResource(it), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = if (moto.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
            if (moto.error) ActionButton(stringResource(R.string.motorcycle_retry), enabled = !moto.working,
                onClick = actions.onRefreshMotorcycle)
        }
    }
    if (!moto.loaded) return
    item("motorcycle_profile") { MotorcycleProfileCard(state, actions, editable) }
    item("motorcycle_gps") {
        SectionCard(stringResource(R.string.motorcycle_gps_title)) {
            SupportingText(stringResource(R.string.motorcycle_gps_separate))
            Text(when {
                moto.gpsReadFailed -> stringResource(R.string.motorcycle_gps_error)
                moto.gpsTripCount == 0 -> stringResource(R.string.motorcycle_gps_empty)
                else -> pluralStringResource(R.plurals.motorcycle_gps_value, moto.gpsTripCount,
                    String.format(Locale.getDefault(), "%.2f", (moto.gpsDistanceMeters ?: 0.0) / 1000), moto.gpsTripCount)
            })
            if (state.tripActive) SupportingText(stringResource(R.string.motorcycle_gps_completed_only))
            if (moto.gpsReadFailed) ActionButton(stringResource(R.string.motorcycle_retry),
                enabled = !moto.working, onClick = actions.onRefreshMotorcycle)
        }
    }
    item("motorcycle_maintenance_editor") { MaintenanceEditor(state, actions, editable) }
    moto.data.maintenance.forEach { record ->
        item("maintenance_${record.id}") { MaintenanceCard(record, state, actions, editable) }
    }
}

@Composable
private fun MotorcycleProfileCard(state: CopilotUiState, actions: CopilotActions, editable: Boolean) {
    val moto = state.motorcycle
    val saved = moto.data.profile
    var name by rememberSaveable(saved?.name) { mutableStateOf(saved?.name.orEmpty()) }
    var model by rememberSaveable(saved?.model) { mutableStateOf(saved?.model.orEmpty()) }
    var km by rememberSaveable(saved?.odometerMeters) { mutableStateOf(manualKilometers(saved?.odometerMeters)) }
    var catalogId by rememberSaveable(saved?.catalogId) { mutableStateOf(saved?.catalogId) }
    var catalogOpen by rememberSaveable { mutableStateOf(false) }
    val selected = MotorcycleCatalog.find(catalogId)
    val draft = MotorcycleDraft(name, model, km, catalogId)
    val changed = saved == null || name != saved.name || model != saved.model ||
        km != manualKilometers(saved.odometerMeters) || catalogId != saved.catalogId
    SectionCard(stringResource(R.string.motorcycle_profile)) {
        Image(painterResource(selected?.artwork ?: R.drawable.ic_ignitor_vector),
            stringResource(R.string.motorcycle_catalog_art), Modifier.fillMaxWidth().heightIn(max = Glass.motorcyclePhotoHeight))
        Text(selected?.model ?: saved?.model?.takeIf { it.isNotBlank() } ?: stringResource(R.string.motorcycle_catalog_unselected),
            style = MaterialTheme.typography.titleLarge)
        ActionButton(stringResource(R.string.motorcycle_catalog_choose), enabled = editable) { catalogOpen = true }
        SupportingText(stringResource(R.string.motorcycle_catalog_hint))
        if (saved != null && saved.catalogId == null) SupportingText(stringResource(R.string.motorcycle_legacy_model))
        if (saved?.photoName != null) {
            SupportingText(stringResource(R.string.motorcycle_legacy_photo))
            ActionButton(stringResource(R.string.motorcycle_remove_photo), enabled = editable, destructive = true,
                onClick = actions.onRemoveMotorcyclePhoto)
        }
        if (catalogOpen) AlertDialog(onDismissRequest = { catalogOpen = false },
            title = { Text(stringResource(R.string.motorcycle_catalog_choose)) }, text = {
                Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    MotorcycleCatalog.entries.forEach { entry ->
                        Row(Modifier.fillMaxWidth().heightIn(min = Glass.iconButtonSize)
                            .selectable(selected = entry.id == catalogId, enabled = editable, role = Role.RadioButton) {
                                catalogId = entry.id; model = entry.model; catalogOpen = false
                            }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = entry.id == catalogId, onClick = null, enabled = editable)
                            Text(entry.model)
                        }
                    }
                    SupportingText(stringResource(R.string.motorcycle_catalog_hint))
                }
            }, confirmButton = { TextButton(onClick = { catalogOpen = false }) { Text(stringResource(R.string.ride_done)) } })
        MotoField(name, { name = it }, R.string.motorcycle_name, editable, max = 60)
        MotoField(km, { km = it }, R.string.motorcycle_odometer, editable, decimal = true,
            error = km.isNotBlank() && parseManualKilometers(km) == null)
        SupportingText(stringResource(R.string.motorcycle_odometer_hint))
        if (km.isNotBlank() && parseManualKilometers(km) == null) SupportingText(stringResource(R.string.motorcycle_km_invalid))
        ActionButton(stringResource(R.string.motorcycle_save), enabled = editable && changed && draft.isValid() && (catalogId != null || saved != null), primary = true) {
            actions.onSaveMotorcycle(draft)
        }
        if (changed && saved != null) ActionButton(stringResource(R.string.motorcycle_discard), enabled = editable) {
            name = saved.name; model = saved.model; km = manualKilometers(saved.odometerMeters); catalogId = saved.catalogId
        }
    }
}

@Composable
private fun MaintenanceEditor(state: CopilotUiState, actions: CopilotActions, editable: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    SectionCard(stringResource(R.string.motorcycle_maintenance)) {
        SupportingText(stringResource(R.string.motorcycle_maintenance_hint))
        if (state.motorcycle.data.maintenance.isEmpty()) SupportingText(stringResource(R.string.motorcycle_maintenance_empty))
        ActionButton(stringResource(R.string.motorcycle_add_maintenance), enabled = editable && state.motorcycle.data.profile != null && !open) { open = true }
        if (open) MaintenanceForm(null, state, actions, editable) { open = false }
    }
}

@Composable
private fun MaintenanceCard(record: MaintenanceRecord, state: CopilotUiState, actions: CopilotActions, editable: Boolean) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    SectionCard(record.title) {
        Text(stringResource(R.string.motorcycle_service_date, record.date.toString()))
        record.odometerMeters?.let { Text(stringResource(R.string.motorcycle_service_km, manualKilometers(it))) }
        if (record.notes.isNotBlank()) SupportingText(record.notes)
        record.nextDate?.let { Text(stringResource(R.string.motorcycle_due_date, it.toString())) }
        record.nextOdometerMeters?.let { Text(stringResource(R.string.motorcycle_due_km, manualKilometers(it))) }
        if (record.isDue(LocalDate.now(), state.motorcycle.data.profile?.odometerMeters)) {
            Text(stringResource(R.string.motorcycle_due), style = MaterialTheme.typography.labelLarge)
        }
        if (record.nextOdometerMeters != null && state.motorcycle.data.profile?.odometerMeters == null) {
            SupportingText(stringResource(R.string.motorcycle_due_km_unknown))
        }
        if (!editing) ActionButton(stringResource(R.string.motorcycle_edit_maintenance), enabled = editable) { editing = true }
        if (editing) MaintenanceForm(record, state, actions, editable) { editing = false }
        ActionButton(stringResource(R.string.motorcycle_delete_maintenance), enabled = editable, destructive = true) { deleting = true }
        if (deleting) AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.motorcycle_delete_maintenance)) },
            text = { Text(stringResource(R.string.motorcycle_delete_question, record.title)) },
            confirmButton = { TextButton(enabled = editable, onClick = {
                deleting = false; actions.onDeleteMaintenance(record.id)
            }) { Text(stringResource(R.string.motorcycle_delete_maintenance), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.motorcycle_cancel)) } }
        )
    }
}

@Composable
private fun MaintenanceForm(record: MaintenanceRecord?, state: CopilotUiState, actions: CopilotActions, editable: Boolean, close: () -> Unit) {
    var title by rememberSaveable(record?.id) { mutableStateOf(record?.title.orEmpty()) }
    var date by rememberSaveable(record?.id) { mutableStateOf(record?.date?.toString() ?: LocalDate.now().toString()) }
    var km by rememberSaveable(record?.id) { mutableStateOf(manualKilometers(record?.odometerMeters)) }
    var notes by rememberSaveable(record?.id) { mutableStateOf(record?.notes.orEmpty()) }
    var nextDate by rememberSaveable(record?.id) { mutableStateOf(record?.nextDate?.toString().orEmpty()) }
    var nextKm by rememberSaveable(record?.id) { mutableStateOf(manualKilometers(record?.nextOdometerMeters)) }
    var pendingRevision by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.motorcycle.revision, state.motorcycle.error) {
        if (state.motorcycle.error) pendingRevision = null
        else if (pendingRevision?.let { state.motorcycle.revision > it } == true) close()
    }
    val draft = MaintenanceDraft(record?.id, title, date, km, notes, nextDate, nextKm)
    MotoField(title, { title = it }, R.string.motorcycle_service_title, editable, max = 80)
    MotoField(date, { date = it }, R.string.motorcycle_service_date_input, editable, max = 10)
    MotoField(km, { km = it }, R.string.motorcycle_service_km_input, editable, decimal = true)
    MotoField(notes, { notes = it }, R.string.motorcycle_service_notes, editable, max = 600, multiline = true)
    MotoField(nextDate, { nextDate = it }, R.string.motorcycle_due_date_input, editable, max = 10)
    MotoField(nextKm, { nextKm = it }, R.string.motorcycle_due_km_input, editable, decimal = true)
    SupportingText(stringResource(R.string.motorcycle_maintenance_validation))
    ActionButton(stringResource(R.string.motorcycle_save_maintenance), enabled = editable && draft.isValid(LocalDate.now()), primary = true) {
        pendingRevision = state.motorcycle.revision
        actions.onSaveMaintenance(draft)
    }
    ActionButton(stringResource(R.string.motorcycle_cancel), enabled = !state.motorcycle.working, onClick = close)
}

@Composable
private fun MotoField(value: String, change: (String) -> Unit, label: Int, enabled: Boolean,
    max: Int = 16, decimal: Boolean = false, error: Boolean = false, multiline: Boolean = false) {
    OutlinedTextField(value, { if (it.length <= max) change(it) }, label = { Text(stringResource(label)) },
        enabled = enabled, singleLine = !multiline, isError = error, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text))
}
