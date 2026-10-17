package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R

/** Licenses ship with the app and remain available without a browser or network. */
@Composable
fun ThirdPartyNoticesCard() {
    var visible by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.licenses_title)) {
        SupportingText(stringResource(R.string.candidate_noncommercial))
        ActionButton(stringResource(R.string.licenses_open), onClick = { visible = true })
    }
    if (visible) {
        val assets = LocalContext.current.assets
        val notices = remember(assets) {
            val files = listOf("NOTICE.txt") + assets.list("licenses").orEmpty().sorted().filter { it != "NOTICE.txt" }
            files.flatMap { name ->
                listOf(name) + assets.open("licenses/$name").bufferedReader().use { it.readText() }
                    .split(Regex("\\r?\\n\\s*\\r?\\n")).filter(String::isNotBlank)
            }
        }
        AlertDialog(onDismissRequest = { visible = false },
            title = { Text(stringResource(R.string.licenses_title)) },
            text = { LazyColumn(Modifier.height(320.dp)) { items(notices) { Text(it) } } },
            confirmButton = { TextButton(onClick = { visible = false }) {
                Text(stringResource(R.string.licenses_close))
            } })
    }
}
