package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.gust4av033.appcopilotomoto.R

enum class DisclosureKind(@param:StringRes val title: Int, @param:StringRes val explanation: Int) {
    NOTIFICATIONS(R.string.disclosure_notifications_title, R.string.disclosure_notifications_body),
    OVERLAY(R.string.disclosure_overlay_title, R.string.disclosure_overlay_body),
    MICROPHONE(R.string.disclosure_microphone_title, R.string.disclosure_microphone_body),
    CONTACTS(R.string.disclosure_contacts_title, R.string.disclosure_contacts_body),
    LOCATION(R.string.disclosure_location_title, R.string.disclosure_location_body)
}

@Composable
fun ProminentDisclosureDialog(kind: DisclosureKind, onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(kind.title)) },
        text = { Text(stringResource(kind.explanation)) },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.disclosure_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.disclosure_cancel)) } })
}
