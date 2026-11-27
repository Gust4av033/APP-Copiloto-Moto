package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.gust4av033.appcopilotomoto.*
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.theme.Glass

@Composable
internal fun MapLayersDialog(online: Boolean, vegetation: Boolean, result: MapCatalogResult?, updating: Boolean,
    cameraMode: TripMapCameraMode, onMode: (TripMapCameraMode) -> Unit, onVegetation: (Boolean) -> Unit,
    onRefresh: () -> Unit, onFitTrace: (() -> Unit)?, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.map_layers_title)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Glass.mapDetailsGap)) {
            ToggleSettingRow(stringResource(R.string.map_vegetation_title), vegetation, onCheckedChange = onVegetation)
            SupportingText(stringResource(R.string.map_vegetation_hint))
            for ((mode, label) in listOf(TripMapCameraMode.FOLLOW_HEADING to R.string.trip_map_camera_heading,
                TripMapCameraMode.NORTH_UP to R.string.trip_map_camera_north, TripMapCameraMode.FREE to R.string.trip_map_camera_free)) {
                Row(Modifier.fillMaxWidth().heightIn(min = Glass.iconButtonSize).selectable(cameraMode == mode,
                    role = Role.RadioButton, onClick = { onMode(mode) }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(cameraMode == mode, onClick = null)
                    Text(stringResource(label))
                }
            }
            onFitTrace?.let { ActionButton(stringResource(R.string.trip_map_fit), onClick = it) }
            result?.version?.let { SupportingText(stringResource(R.string.map_update_version, it.version)) }
            val message = when {
                updating -> R.string.map_update_busy
                result?.status == MapCatalogStatus.UPDATED -> R.string.map_update_new
                result?.status == MapCatalogStatus.UNCHANGED -> R.string.map_update_same
                result?.status == MapCatalogStatus.PREVIOUS -> R.string.map_update_previous
                result?.status == MapCatalogStatus.FAILED -> R.string.map_update_failed
                result?.status == MapCatalogStatus.WAIT -> R.string.map_update_wait
                result?.version == null -> R.string.map_update_not_checked
                else -> R.string.map_update_saved
            }
            SupportingText(stringResource(message))
            ActionButton(stringResource(R.string.map_update), enabled = online && !updating, onClick = onRefresh)
            if (!online) SupportingText(stringResource(R.string.map_update_offline))
            SupportingText(stringResource(R.string.map_update_explanation))
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ride_done)) } })
}
