package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [BuildConfig.MIN_SUPPORTED_SDK, 35])
class HeadsetSelectionStoreTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private val evo=HeadsetIdentity(12,7,"AA:BB:CC:DD:EE:FF","Dime Evo")
    @Before fun clear() { HeadsetSelection.select(null);context.getSharedPreferences("headset_selection",0).edit().clear().commit() }
    @Test fun stableChoiceResolvesNewAndroidIdAfterRestart() { HeadsetSelection.saveChoice(context,evo);HeadsetSelection.select(null);HeadsetSelection.restore(context);assertEquals(evo.copy(id=99),HeadsetSelection.resolve(listOf(evo.copy(id=99)))) }
    @Test fun missingChoiceCannotSilentlySwitchToAnotherHeadset() { HeadsetSelection.saveChoice(context,evo);HeadsetSelection.select(null);HeadsetSelection.restore(context);assertNotNull(HeadsetSelection.preferred);assertNull(HeadsetSelection.resolve(listOf(evo.copy(address="other",name="Y12 Pro")))) }
    @Test fun ambiguousIdentityRequiresAChoice() { HeadsetSelection.saveChoice(context,evo);HeadsetSelection.select(null);HeadsetSelection.restore(context);assertNull(HeadsetSelection.resolve(listOf(evo.copy(id=99),evo.copy(id=100)))) }
    @Test fun anonymousManualChoiceRequiresReselectionAfterRestart() { HeadsetSelection.saveChoice(context,evo.copy(address=""));HeadsetSelection.select(null);HeadsetSelection.restore(context);assertNotNull(HeadsetSelection.preferred);assertNull(HeadsetSelection.resolve(listOf(evo.copy(id=99,address="")))) }
    @Test fun automaticChoiceClearsSavedManualChoice() { HeadsetSelection.saveChoice(context,evo);HeadsetSelection.saveChoice(context,null);HeadsetSelection.restore(context);assertNull(HeadsetSelection.preferred) }
    @Test fun routeTypeCannotChangeUnderSavedChoice() { HeadsetSelection.saveChoice(context,evo);HeadsetSelection.select(null);HeadsetSelection.restore(context);assertNull(HeadsetSelection.resolve(listOf(evo.copy(id=99,type=26)))) }
}
