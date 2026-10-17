package com.gust4av033.appcopilotomoto

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [BuildConfig.MIN_SUPPORTED_SDK, 35])
class UserSettingsStoreTest {
    private lateinit var context: Application
    @Before fun clear() { context=RuntimeEnvironment.getApplication(); context.getSharedPreferences("ui_settings",0).edit().clear().commit() }
    @Test fun freshInstallationUsesAlexaBalancedAndHiddenTools() { val s=UserSettingsStore(context); assertEquals("ALEXA" to 80f,s.loadVoice()); assertFalse(s.developerEnabled(false)); assertFalse(s.developerEnabled(true)) }
    @Test fun legacyValuesMigrateOnlyWhenNoPreferencesExist() { val s=UserSettingsStore(context); assertEquals("HEY_JARVIS" to 65f,s.loadVoice("HEY_JARVIS",65f)); assertEquals("HEY_JARVIS" to 65f,UserSettingsStore(context).loadVoice("ALEXA",80f)) }
    @Test fun voiceChoiceSurvivesPreferenceFileReload() {
        val s=UserSettingsStore(context); s.saveVoice("HEY_JARVIS",60f)
        // Flush pending apply writes, check the actual XML and evict the Android cache.
        assertTrue(context.getSharedPreferences("ui_settings",0).edit().putBoolean("flush",true).commit())
        val file=java.io.File(context.applicationInfo.dataDir,"shared_prefs/ui_settings.xml")
        assertTrue(file.readText().contains("HEY_JARVIS")); assertTrue(file.readText().contains("60.0"))
        val field=Class.forName("android.app.ContextImpl").getDeclaredField("sSharedPrefsCache"); field.isAccessible=true
        (field.get(null) as MutableMap<*, *>).clear()
        assertEquals("HEY_JARVIS" to 60f,UserSettingsStore(context).loadVoice())
    }
    @Test fun invalidVoiceValuesFallBackSafely() { val s=UserSettingsStore(context); s.saveVoice("arbitrary phrase",Float.NaN); assertEquals("ALEXA" to 80f,s.loadVoice()) }
    @Test fun sensitivityCannotExceedSupportedRange() { val s=UserSettingsStore(context);s.saveVoice("ALEXA",100f);assertEquals(85f,s.loadVoice().second,0f) }
    @Test fun debugOptInDoesNotUnlockReleaseAfterVariantChange() { val s=UserSettingsStore(context);s.setDeveloperEnabled(true,true);assertTrue(s.developerEnabled(true));assertFalse(s.developerEnabled(false)) }
    @Test fun developerOptInCanBeDisabledPersistently() { val s=UserSettingsStore(context);s.setDeveloperEnabled(false,true);s.setDeveloperEnabled(false,false);assertFalse(UserSettingsStore(context).developerEnabled(false)) }
}
