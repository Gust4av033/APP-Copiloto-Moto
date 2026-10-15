package com.gust4av033.appcopilotomoto.presentation

import androidx.compose.runtime.Immutable
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.RoutedCommand
import com.gust4av033.appcopilotomoto.TestIntent
import com.gust4av033.appcopilotomoto.modeNumber

@Immutable
data class CommandUi(val titleRes: Int, val detailRes: Int? = null, val detailValue: String = "")

/** Proyección de la orden resuelta. Interpretarla no confirma que se haya ejecutado. */
fun RoutedCommand.toCommandUi(): CommandUi = when (intent) {
    TestIntent.MEDIA_NEXT -> CommandUi(R.string.command_next)
    TestIntent.MEDIA_PREVIOUS -> CommandUi(R.string.command_previous)
    TestIntent.MEDIA_PLAY -> CommandUi(R.string.command_play)
    TestIntent.MEDIA_PAUSE -> CommandUi(R.string.command_pause)
    TestIntent.MEDIA_SEARCH -> CommandUi(R.string.command_music_search, R.string.command_music_query,
        if (musicTitleInEnglish) "Título en inglés después del tono" else musicQuery.orEmpty())
    TestIntent.VOLUME_UP -> CommandUi(R.string.command_volume_up)
    TestIntent.VOLUME_DOWN -> CommandUi(R.string.command_volume_down)
    TestIntent.VOLUME_SET -> CommandUi(R.string.command_volume_set,
        if (volume?.percent == true) R.string.command_percent else R.string.command_level,
        volume?.amount?.toString().orEmpty())
    TestIntent.OPEN_CONTACT_CHAT -> CommandUi(R.string.command_contact_chat, R.string.command_contact_parameter,
        "${chatApp?.label.orEmpty()}: ${contactName.orEmpty()}")
    TestIntent.DRAFT_CONTACT_MESSAGE -> CommandUi(R.string.command_chat_draft, R.string.command_contact_parameter,
        "${chatApp?.label.orEmpty()}: ${contactName.orEmpty()}")
    TestIntent.SEARCH_PLACE -> CommandUi(R.string.command_search_place, R.string.command_place_query, placeQuery.orEmpty())
    TestIntent.NEXT_TURN -> CommandUi(R.string.command_next_turn)
    TestIntent.HOW_LONG -> CommandUi(R.string.command_how_long)
    TestIntent.TRIP_STATUS -> CommandUi(R.string.command_trip_status)
    TestIntent.NAVIGATION_REQUEST_STATUS -> CommandUi(R.string.command_navigation_request_status)
    TestIntent.NAVIGATE -> CommandUi(R.string.command_navigate, R.string.command_navigation_parameter,
        when (navigationRequest) {
            com.gust4av033.appcopilotomoto.NavigationRequest.HOME -> "Casa"
            com.gust4av033.appcopilotomoto.NavigationRequest.WORK -> "Trabajo"
            else -> "Última solicitud de APP"
        })
    TestIntent.OPEN_APP -> CommandUi(R.string.command_open_app, R.string.command_app_parameter, appName.orEmpty())
    TestIntent.CALL_NAMED -> CommandUi(R.string.command_call, R.string.command_contact_parameter, contactName.orEmpty())
    TestIntent.CALL_SELECTED -> CommandUi(R.string.command_call, R.string.command_fixed_contact)
    TestIntent.CONFIRM_CALL -> CommandUi(R.string.command_confirm_call)
    TestIntent.CANCEL -> CommandUi(R.string.command_cancel)
    TestIntent.STOP_TTS -> CommandUi(R.string.command_silence)
    TestIntent.STOP_SESSION -> CommandUi(R.string.command_stop_session)
    TestIntent.SWITCH_MODE -> CommandUi(R.string.command_switch_mode, R.string.command_mode_number, audioMode?.modeNumber?.toString().orEmpty())
    TestIntent.OPEN_ASSISTANT -> CommandUi(R.string.command_open_assistant)
    TestIntent.UNKNOWN -> CommandUi(R.string.command_unknown)
}

