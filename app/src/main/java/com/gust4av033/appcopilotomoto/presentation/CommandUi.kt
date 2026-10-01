package com.gust4av033.appcopilotomoto.presentation

import androidx.compose.runtime.Immutable
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.RoutedCommand
import com.gust4av033.appcopilotomoto.TestIntent

@Immutable
data class CommandUi(val titleRes: Int, val detailRes: Int? = null, val detailValue: String = "")

/** Proyección de la orden resuelta. Interpretarla no confirma que se haya ejecutado. */
fun RoutedCommand.toCommandUi(): CommandUi = when (intent) {
    TestIntent.MEDIA_NEXT -> CommandUi(R.string.command_next)
    TestIntent.MEDIA_PREVIOUS -> CommandUi(R.string.command_previous)
    TestIntent.MEDIA_PLAY -> CommandUi(R.string.command_play)
    TestIntent.MEDIA_PAUSE -> CommandUi(R.string.command_pause)
    TestIntent.MEDIA_SEARCH -> CommandUi(R.string.command_music_search, R.string.command_music_query, musicQuery.orEmpty())
    TestIntent.VOLUME_UP -> CommandUi(R.string.command_volume_up)
    TestIntent.VOLUME_DOWN -> CommandUi(R.string.command_volume_down)
    TestIntent.VOLUME_SET -> CommandUi(R.string.command_volume_set,
        if (volume?.percent == true) R.string.command_percent else R.string.command_level,
        volume?.amount?.toString().orEmpty())
    TestIntent.OPEN_CONTACT_CHAT -> CommandUi(R.string.command_contact_chat, R.string.command_contact_parameter,
        "${chatApp?.label.orEmpty()}: ${contactName.orEmpty()}")
    TestIntent.OPEN_APP -> CommandUi(R.string.command_open_app, R.string.command_app_parameter, appName.orEmpty())
    TestIntent.CALL_NAMED -> CommandUi(R.string.command_call, R.string.command_contact_parameter, contactName.orEmpty())
    TestIntent.CALL_SELECTED -> CommandUi(R.string.command_call, R.string.command_fixed_contact)
    TestIntent.CONFIRM_CALL -> CommandUi(R.string.command_confirm_call)
    TestIntent.CANCEL -> CommandUi(R.string.command_cancel)
    TestIntent.STOP_TTS -> CommandUi(R.string.command_silence)
    TestIntent.STOP_SESSION -> CommandUi(R.string.command_stop_session)
    TestIntent.UNKNOWN -> CommandUi(R.string.command_unknown)
}

