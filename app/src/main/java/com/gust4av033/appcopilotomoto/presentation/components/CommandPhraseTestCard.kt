package com.gust4av033.appcopilotomoto.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gust4av033.appcopilotomoto.MinimalIntentRouter
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.presentation.CommandUi
import com.gust4av033.appcopilotomoto.presentation.toCommandUi

@Composable
fun CommandInterpretation(command: CommandUi) {
    Text(stringResource(command.titleRes), style = MaterialTheme.typography.titleSmall)
    command.detailRes?.let { SupportingText(stringResource(it, command.detailValue)) }
}

/** Usa exclusivamente reglas locales: no abre micrófonos, apps ni llamadas. */
@Composable
fun CommandPhraseTestCard() {
    var phrase by rememberSaveable { mutableStateOf("") }
    val interpreted = remember(phrase) {
        phrase.takeIf { it.isNotBlank() }?.let { MinimalIntentRouter.route(it).toCommandUi() }
    }
    SectionCard(stringResource(R.string.phrase_check_title)) {
        SupportingText(stringResource(R.string.phrase_check_hint))
        OutlinedTextField(value = phrase, onValueChange = { phrase = it.take(200) },
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.phrase_check_label)) },
            placeholder = { Text(stringResource(R.string.phrase_check_example)) }, minLines = 2, maxLines = 4)
        interpreted?.let { CommandInterpretation(it) }
        SupportingText(stringResource(R.string.phrase_check_scope))
        SupportingText(stringResource(R.string.phrase_check_limits))
    }
}
