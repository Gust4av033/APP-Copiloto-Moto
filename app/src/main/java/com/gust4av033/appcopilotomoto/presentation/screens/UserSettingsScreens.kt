package com.gust4av033.appcopilotomoto.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gust4av033.appcopilotomoto.R
import com.gust4av033.appcopilotomoto.SensitivityPreset
import com.gust4av033.appcopilotomoto.TripGreetingMode
import com.gust4av033.appcopilotomoto.TripGreetingSettings
import com.gust4av033.appcopilotomoto.presentation.*
import com.gust4av033.appcopilotomoto.presentation.components.*

fun LazyListScope.SettingsScreen(
    state: CopilotUiState, actions: CopilotActions, section: SettingsSection,
    onOpenSection: (SettingsSection) -> Unit, onOpenCommands: () -> Unit, onOpenTests: () -> Unit
) {
    if (state.busy) item("settings_busy") { SupportingText(stringResource(R.string.settings_changes_parked)) }
    if (state.configurationNotice.isNotBlank()) item("settings_notice") { SupportingText(state.configurationNotice) }
    when (section.visibleFor(state)) {
        SettingsSection.OVERVIEW -> {
            SettingsSection.userSections.forEach { category -> item(category.name) {
                val summary = when (category) {
                    SettingsSection.HEADSET -> if (state.audioDevices.headsets.isNotEmpty() && !state.audioDevices.selectionMissing)
                        stringResource(R.string.settings_headset_ready) else stringResource(R.string.settings_headset_missing)
                    SettingsSection.DESTINATIONS -> stringResource(if (state.navigationAvailable) R.string.navigation_ready else R.string.navigation_missing,
                        state.navigationProvider.label)
                    else -> null
                }
                SettingsMenuRow(category, summary?.takeIf { it.isNotBlank() }) { onOpenSection(category) }
            } }
            if (state.developerEnabled) item("developer_entry") {
                SettingsMenuRow(SettingsSection.DEVELOPER, null) { onOpenSection(SettingsSection.DEVELOPER) }
            }
        }
        SettingsSection.HEADSET -> {
            item("activation_entry") { SettingsMenuRow(SettingsSection.ACTIVATION, null) { onOpenSection(SettingsSection.ACTIVATION) } }
            item("audio_entry") { SettingsMenuRow(SettingsSection.AUDIO, null) { onOpenSection(SettingsSection.AUDIO) } }
            item("appearance_entry") { SettingsMenuRow(SettingsSection.APPEARANCE, null) { onOpenSection(SettingsSection.APPEARANCE) } }
        }
        SettingsSection.DESTINATIONS -> {
            item("navigation_entry") { SettingsMenuRow(SettingsSection.NAVIGATION, null) { onOpenSection(SettingsSection.NAVIGATION) } }
            item("integrations_entry") { SettingsMenuRow(SettingsSection.INTEGRATIONS, null) { onOpenSection(SettingsSection.INTEGRATIONS) } }
        }
        SettingsSection.VOICE_PRIVACY -> {
            item("calls_entry") { SettingsMenuRow(SettingsSection.CALLS, null) { onOpenSection(SettingsSection.CALLS) } }
            item("privacy_entry") { SettingsMenuRow(SettingsSection.PRIVACY, null) { onOpenSection(SettingsSection.PRIVACY) } }
            item("about_entry") { SettingsMenuRow(SettingsSection.ABOUT, null) { onOpenSection(SettingsSection.ABOUT) } }
            item("privacy_link") { ActionButton(stringResource(R.string.privacy_policy_button), iconRes = R.drawable.ic_shield,
                onClick = actions.onOpenPrivacyPolicy) }
        }
        SettingsSection.ACTIVATION -> {
            item("activation_mode") { SectionCard(stringResource(R.string.settings_input)) {
                ChoiceGroup(state.inputs.filter { it.id != "PHONE_WAKE" },
                    if (state.manualActivationOnly) "BUTTON" else state.inputId, !state.busy, actions.onInputChange)
                SupportingText(stringResource(R.string.settings_voice_audio_hint))
            } }
            item("headset_button") { TripButtonSettingsCard(state, actions) }
            item("greeting_mode") { GreetingSettingsCard(state) }
            item("wake_preferences") { SectionCard(stringResource(R.string.settings_phrase)) {
                if (state.buttonMode) SupportingText(stringResource(R.string.settings_phrase_manual_inactive))
                ChoiceGroup(state.phrases, state.phraseId, !state.busy, actions.onPhraseChange)
                SupportingText(stringResource(R.string.settings_phrase_hint))
                Text(stringResource(R.string.settings_sensitivity_title), style = MaterialTheme.typography.titleSmall)
                ChoiceGroup(listOf(ChoiceUi("LOW", R.string.settings_sensitivity_low),
                    ChoiceUi("BALANCED", R.string.settings_sensitivity_balanced), ChoiceUi("HIGH", R.string.settings_sensitivity_high)),
                    SensitivityPreset.closest(state.sensitivity).name, !state.busy) {
                    actions.onSensitivityChange(SensitivityPreset.valueOf(it).value)
                }
                SupportingText(stringResource(R.string.settings_sensitivity_simple_hint))
            } }
            item("languages") { UserLanguageCard(state, actions) }
        }
        SettingsSection.AUDIO -> {
            item("user_devices") { UserDevicesCard(state, actions) }
            item("media_access") { MediaAccessCard(state, actions) }
            item("music_player") { MusicPlayerCard(state, actions) }
            item("audio_help") { BasicHelpLink(actions) }
        }
        SettingsSection.APPEARANCE -> AppearanceSettings(state, actions)
        SettingsSection.NAVIGATION -> {
            item("navigation") { NavigationCard(state, actions) }
            if (com.gust4av033.appcopilotomoto.BuildConfig.IS_PLAYSTORE_BUILD) item("waze_notification_access") {
                SectionCard(stringResource(R.string.navigation_waze_notices)) {
                    ToggleSettingRow(stringResource(R.string.navigation_waze_notices_toggle), state.wazeObservationEnabled,
                        enabled = !state.busy, onCheckedChange = actions.onWazeObservationChange)
                    SupportingText(stringResource(when {
                        !state.wazeObservationEnabled -> R.string.navigation_waze_notices_disabled
                        state.wazeObservationGranted -> R.string.navigation_waze_notices_ready
                        else -> R.string.navigation_waze_notices_permission
                    }))
                    ActionButton(stringResource(R.string.navigation_waze_notices_button), iconRes = R.drawable.ic_trip,
                        enabled = !state.busy && state.wazeObservationEnabled, onClick = actions.onWazeObservationPermission)
                }
            }
            if (state.developerEnabled) item("waze_observation") {
                com.gust4av033.appcopilotomoto.presentation.components.WazeObservationCard(state, actions)
            }
        }
        SettingsSection.INTEGRATIONS -> {
            item("integrations") { IntegrationsCard(state, actions, includeNavigation = false) }
            item("calls_entry") { SettingsMenuRow(SettingsSection.CALLS, null) { onOpenSection(SettingsSection.CALLS) } }
            item("music_entry") { SettingsMenuRow(SettingsSection.AUDIO, null) { onOpenSection(SettingsSection.AUDIO) } }
            item("commands_entry") { QuickLink(stringResource(R.string.home_view_commands),
                stringResource(R.string.home_commands_short), R.drawable.ic_commands, onOpenCommands) }
        }
        SettingsSection.CALLS -> CallsSettings(state, actions)
        SettingsSection.PRIVACY -> {
            item("trip_location") { SectionCard(stringResource(R.string.trip_location_title)) {
                ToggleSettingRow(stringResource(R.string.trip_location_title), state.tripLocationEnabled,
                    enabled = !state.busy, onCheckedChange = actions.onTripLocationChange)
                SupportingText(stringResource(R.string.trip_location_explanation))
                SupportingText(if (!state.tripLocationEnabled) stringResource(R.string.trip_location_off)
                    else if (state.tripLocationAvailable) stringResource(R.string.trip_location_ready)
                    else stringResource(R.string.trip_location_unavailable))
            } }
            item("forget_trip_history") { SectionCard(stringResource(R.string.trip_history_title)) {
                SupportingText(stringResource(R.string.trip_history_explanation))
                ActionButton(stringResource(R.string.trip_history_forget), enabled = !state.busy, destructive = true,
                    onClick = actions.onForgetTripHistory)
            } }
            item("permissions") { PermissionsCard(state, actions) }
            item("media_permission") { MediaAccessCard(state, actions) }
            item("overlay_permission") { SectionCard(stringResource(R.string.overlay_settings_title)) {
                SupportingText(stringResource(R.string.settings_permission_overlay))
                SupportingText(stringResource(if (state.overlayPermissionGranted) R.string.settings_permission_ready else R.string.settings_permission_missing))
                ActionButton(stringResource(R.string.overlay_permission_button), iconRes = R.drawable.ic_display,
                    enabled = !state.busy, onClick = actions.onOverlayPermission)
            } }
            item("privacy_scope") { SectionCard(stringResource(R.string.settings_privacy_data)) {
                SupportingText(stringResource(R.string.settings_privacy_scope))
                SupportingText(stringResource(R.string.settings_privacy_future))
            } }
            item("privacy_policy") { ActionButton(stringResource(R.string.privacy_policy_button), iconRes = R.drawable.ic_shield,
                onClick = actions.onOpenPrivacyPolicy) }
        }
        SettingsSection.ABOUT -> {
            item("about_version") { SectionCard(stringResource(R.string.settings_category_about)) {
                SupportingText(stringResource(R.string.settings_about_description))
                ActionButton(stringResource(R.string.settings_installed_version, state.installedVersion),
                    iconRes = R.drawable.ic_info, enabled = !state.busy, onClick = actions.onVersionTap)
            } }
            item("basic_help") { BasicHelpLink(actions) }
            if (state.developerEnabled) item("developer_about") {
                SettingsMenuRow(SettingsSection.DEVELOPER, null) { onOpenSection(SettingsSection.DEVELOPER) }
            }
        }
        SettingsSection.DEVELOPER, SettingsSection.TOOLS -> {
            if (state.developerEnabled) {
                item("developer_access") { SectionCard(stringResource(R.string.settings_developer)) {
                    SupportingText(stringResource(if (state.developerToolsAvailable) R.string.settings_developer_debug else R.string.settings_developer_read_only))
                    ActionButton(stringResource(R.string.open_tests), iconRes = R.drawable.ic_tests, onClick = onOpenTests)
                    ActionButton(stringResource(R.string.settings_developer_disable), iconRes = R.drawable.ic_shield,
                        enabled = !state.busy, onClick = actions.onDisableDeveloper)
                } }
                if (state.developerToolsAvailable) item("experimental_preferences") {
                    SectionCard(stringResource(R.string.settings_experimental)) {
                        ChoiceGroup(state.inputs, state.inputId, !state.busy, actions.onInputChange)
                        ToggleSettingRow(stringResource(R.string.manual_activation_title), state.manualActivationOnly,
                            enabled = !state.busy, onCheckedChange = actions.onManualActivationOnlyChange)
                        SupportingText(stringResource(R.string.manual_activation_hint))
                        Text(stringResource(R.string.settings_sensitivity, state.sensitivity.toInt()))
                        Slider(state.sensitivity, actions.onSensitivityChange, valueRange = 40f..85f, steps = 8, enabled = !state.busy)
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingSettingsCard(state: CopilotUiState) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(TripGreetingSettings.selected(context).name) }
    var nameDraft by rememberSaveable { mutableStateOf(TripGreetingSettings.name(context)) }
    SectionCard(stringResource(R.string.trip_greeting_title)) {
        ChoiceGroup(listOf(ChoiceUi(TripGreetingMode.VOICE_SHORT.name, R.string.trip_greeting_voice),
            ChoiceUi(TripGreetingMode.TONE_ONLY.name, R.string.trip_greeting_tone),
            ChoiceUi(TripGreetingMode.SILENT.name, R.string.trip_greeting_silent)),
            selected, !state.busy) { value ->
            val mode = TripGreetingMode.valueOf(value)
            TripGreetingSettings.select(context, mode)
            selected = mode.name
        }
        OutlinedTextField(value = nameDraft, onValueChange = { input ->
            nameDraft = input.take(TripGreetingSettings.MAX_NAME_LENGTH)
                .filter { it.isLetter() || it == ' ' || it == '-' || it == '\'' }
            TripGreetingSettings.setName(context, nameDraft)
        }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.busy,
            label = { Text(stringResource(R.string.trip_greeting_name_label)) },
            colors = com.gust4av033.appcopilotomoto.presentation.theme.glassTextFieldColors())
        SupportingText(stringResource(R.string.trip_greeting_name_hint))
        SupportingText(stringResource(R.string.trip_greeting_hint))
    }
}

@Composable
private fun BasicHelpLink(actions: CopilotActions) {
    QuickLink(stringResource(R.string.settings_basic_help), stringResource(R.string.settings_basic_help_hint),
        R.drawable.ic_help, actions.onOpenBasicDiagnostics)
}

@Composable
fun MediaAccessCard(state: CopilotUiState, actions: CopilotActions) {
    SectionCard(stringResource(R.string.settings_media_access)) {
        SupportingText(stringResource(R.string.settings_media_access_hint))
        SupportingText(stringResource(if (state.mediaAccessGranted) R.string.trip_button_access_ready else R.string.trip_button_access_missing,
            stringResource(R.string.app_name)))
        SupportingText(stringResource(R.string.settings_media_installation_hint))
        ActionButton(stringResource(if (state.mediaAccessGranted) R.string.trip_button_access_review else R.string.trip_button_access),
            iconRes = R.drawable.ic_play, enabled = !state.busy, onClick = actions.onMediaAccessSettings)
    }
}

@Composable
private fun UserDevicesCard(state: CopilotUiState, actions: CopilotActions) {
    val d = state.audioDevices
    SectionCard(stringResource(R.string.settings_user_headset)) {
        SupportingText(stringResource(R.string.settings_user_headset_hint))
        SupportingText(d.selectedLabel.ifBlank { d.automaticHeadset }.ifBlank { stringResource(R.string.devices_auto_unresolved) })
        if (d.selectionMissing) Text(stringResource(R.string.devices_selection_lost), color = MaterialTheme.colorScheme.error)
        if (!d.permissionGranted) SupportingText(stringResource(R.string.devices_permission_hint))
        if (d.supported && d.permissionGranted) Column(Modifier.selectableGroup()) {
            UserDeviceChoice(stringResource(R.string.devices_automatic), d.selectedId == null, !state.busy) { actions.onSelectHeadset(null) }
            d.headsets.forEach { device -> UserDeviceChoice(device.label, d.selectedId == device.id && !d.selectionMissing, !state.busy) { actions.onSelectHeadset(device.id) } }
        }
        if (d.message.isNotBlank()) SupportingText(stringResource(R.string.settings_audio_read_failed))
        if (state.tripActive && !state.buttonMode) SupportingText(stringResource(R.string.settings_voice_audio_hint))
        ActionButton(stringResource(if (!d.permissionGranted) R.string.devices_enable else R.string.devices_refresh),
            iconRes = R.drawable.ic_audio, enabled = !state.busy, onClick = actions.onRefreshDevices)
        ActionButton(stringResource(R.string.devices_android_controls), iconRes = R.drawable.ic_audio,
            enabled = !state.busy, onClick = actions.onOpenAudioControls)
    }
}

@Composable
private fun UserDeviceChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.weight(1f).padding(start = 12.dp))
    }
}

@Composable
private fun PermissionsCard(state: CopilotUiState, actions: CopilotActions) {
    SectionCard(stringResource(R.string.settings_category_privacy)) {
        listOf(R.string.settings_permission_mic to state.microphoneGranted,
            R.string.settings_permission_bluetooth to state.bluetoothGranted,
            R.string.settings_permission_notifications to state.notificationsGranted,
            R.string.settings_permission_contacts to state.chatContactsGranted,
            *(if (com.gust4av033.appcopilotomoto.BuildConfig.ALLOW_DIRECT_CALL)
                arrayOf(R.string.settings_permission_calls to state.callsGranted) else emptyArray())).forEach { (label, granted) ->
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
            SupportingText(stringResource(if (granted) R.string.settings_permission_ready else R.string.settings_permission_missing))
        }
        SupportingText(stringResource(R.string.settings_permissions_hint))
        ActionButton(stringResource(R.string.settings_open_android_permissions), iconRes = R.drawable.ic_shield,
            enabled = !state.busy, onClick = actions.onOpenAppPermissions)
    }
}

@Composable
private fun UserLanguageCard(state: CopilotUiState, actions: CopilotActions) {
    SectionCard(stringResource(R.string.speech_languages_title)) {
        val phase = state.speechLanguages.phase
        SupportingText(stringResource(when (phase) {
            com.gust4av033.appcopilotomoto.SpeechLanguagePhase.PREPARED -> R.string.speech_languages_prepared
            com.gust4av033.appcopilotomoto.SpeechLanguagePhase.MISSING_ENGLISH -> R.string.speech_languages_missing_english
            com.gust4av033.appcopilotomoto.SpeechLanguagePhase.CHECKING -> R.string.speech_languages_checking
            com.gust4av033.appcopilotomoto.SpeechLanguagePhase.UNSUPPORTED -> R.string.speech_languages_unsupported
            else -> R.string.speech_languages_not_checked
        }))
        SupportingText(stringResource(R.string.speech_languages_hint))
        val available = !state.busy && !state.speechLanguages.downloading && phase != com.gust4av033.appcopilotomoto.SpeechLanguagePhase.CHECKING
        ActionButton(stringResource(R.string.speech_languages_refresh), iconRes = R.drawable.ic_mic, enabled = available, onClick = actions.onRefreshSpeechLanguages)
        if (phase == com.gust4av033.appcopilotomoto.SpeechLanguagePhase.MISSING_ENGLISH || phase == com.gust4av033.appcopilotomoto.SpeechLanguagePhase.FAILED)
            ActionButton(stringResource(R.string.speech_languages_prepare), iconRes = R.drawable.ic_mic, enabled = available, onClick = actions.onPrepareEnglish)
    }
}

fun LazyListScope.BasicDiagnosticsScreen(state: CopilotUiState, actions: CopilotActions) {
    item("basic_intro") { SupportingText(stringResource(R.string.settings_basic_help_hint)) }
    if (state.busy) item("basic_parked") { SupportingText(stringResource(R.string.settings_changes_parked)) }
    item("basic_checklist") { SectionCard(stringResource(R.string.settings_audio_help_title)) {
        SupportingText(stringResource(R.string.settings_audio_help_steps))
        SupportingText(stringResource(R.string.settings_button_help_steps))
        ActionButton(stringResource(R.string.devices_refresh), iconRes = R.drawable.ic_audio, enabled = !state.busy, onClick = actions.onRefreshDevices)
        ActionButton(stringResource(R.string.assistant_select), iconRes = R.drawable.ic_button, enabled = !state.busy, onClick = actions.onSelectAssistant)
    } }
    item("basic_access") { MediaAccessCard(state, actions) }
    item("basic_permissions") { PermissionsCard(state, actions) }
    item("basic_report") { SectionCard(stringResource(R.string.settings_basic_summary)) {
        val connected = state.audioDevices.headsets.isNotEmpty() && !state.audioDevices.selectionMissing
        val report = stringResource(R.string.settings_basic_report, state.installedVersion,
            stringResource(if (state.buttonMode) R.string.mode_button_title else R.string.mode_voice_title),
            stringResource(if (connected) R.string.settings_headset_connected else R.string.settings_headset_missing),
            stringResource(if (state.mediaAccessGranted) R.string.settings_permission_ready else R.string.settings_permission_missing))
        SupportingText(report)
        SupportingText(stringResource(R.string.settings_basic_report_scope))
        ActionButton(stringResource(R.string.settings_copy_basic_summary), iconRes = R.drawable.ic_info,
            onClick = { actions.onCopyBasicDiagnostics(report) })
    } }
}

private fun LazyListScope.AppearanceSettings(state: CopilotUiState, actions: CopilotActions) {

            item(key = "SettingsScreen_2") {
                SectionCard(stringResource(R.string.overlay_settings_title)) {
                    ToggleSettingRow(stringResource(R.string.overlay_settings_toggle),
                        checked = state.overlayEnabled, onCheckedChange = actions.onOverlayEnabledChange)
                    SupportingText(stringResource(R.string.overlay_settings_hint))
                    if (state.overlayEnabled) {
                        SupportingText(stringResource(R.string.overlay_observer_hint))
                    }
                    if (state.overlayEnabled && !state.overlayPermissionGranted) {
                        SupportingText(stringResource(R.string.overlay_permission_hint))
                        ActionButton(stringResource(R.string.overlay_permission_button), iconRes = R.drawable.ic_display,
                            onClick = actions.onOverlayPermission)
                    } else if (state.overlayEnabled && state.tripActive && state.overlayHidden) {
                        SupportingText(stringResource(R.string.overlay_hidden_hint))
                        ActionButton(stringResource(R.string.overlay_show), iconRes = R.drawable.ic_display,
                            onClick = actions.onShowOverlay)
                    }
                    if (state.overlayMessage.isNotBlank()) SupportingText(state.overlayMessage)
                }
            }
            item(key = "SettingsScreen_1") {
                SectionCard(stringResource(R.string.settings_screen_on)) {
                    ToggleSettingRow(stringResource(R.string.settings_screen_on_toggle),
                        checked = state.keepScreenOnDuringTrip, onCheckedChange = actions.onKeepScreenOnChange)
                    SupportingText(stringResource(R.string.settings_screen_on_hint))
                }
            }
        
}
private fun LazyListScope.CallsSettings(state: CopilotUiState, actions: CopilotActions) {

            item(key = "SettingsScreen_9") {
                SectionCard(stringResource(R.string.settings_calls)) {
                    SupportingText(if (com.gust4av033.appcopilotomoto.BuildConfig.ALLOW_DIRECT_CALL)
                        stringResource(R.string.settings_calls_hint)
                        else stringResource(R.string.settings_calls_playstore_hint))
                    ActionButton(stringResource(R.string.calls_enable), enabled = !state.busy,
                        iconRes = R.drawable.ic_phone, onClick = actions.onEnableNamedCalls)
                    SupportingText(stringResource(R.string.calls_fixed_hint))
                    ActionButton(
                        stringResource(if (state.fixedContact == null) R.string.contact_choose else R.string.contact_change),
                        enabled = !state.busy,
                        onClick = actions.onChooseContact
                    )
                    state.fixedContact?.let { contact ->
                        Text(stringResource(R.string.contact_fixed, contact))
                        ActionButton(stringResource(R.string.contact_clear), enabled = !state.busy, onClick = actions.onClearContact)
                    }
                    SupportingText(state.callMessage)
                }
            }
        
}
