package com.gust4av033.appcopilotomoto

import androidx.compose.runtime.MutableState
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class DeveloperRuntimeGateTest {
    private fun state(activity: MainActivity, name: String): MutableState<*> {
        val field=MainActivity::class.java.getDeclaredField(name+"\$delegate");field.isAccessible=true
        return field.get(activity) as MutableState<*>
    }
    private fun assertCapturesBlocked(unlocked: Boolean) {
        val controller=Robolectric.buildActivity(MainActivity::class.java).create()
        val activity=controller.get()
        @Suppress("UNCHECKED_CAST")
        (state(activity,"developerEnabled") as MutableState<Boolean>).value=unlocked
        for (method in listOf("requestProbe","startProbe","startVoiceTest","startWakeTest")) {
            MainActivity::class.java.getDeclaredMethod(method).apply { isAccessible=true }.invoke(activity)
        }
        assertEquals(false,state(activity,"running").value)
        assertEquals(false,state(activity,"listening").value)
        assertEquals(false,state(activity,"wakeEnabled").value)
        controller.destroy()
    }
    @Test fun actualActivityRejectsCapturesWhenLocked() { assertCapturesBlocked(false) }
    @Test fun actualReleaseActivityRejectsCapturesEvenWithOptIn() { assumeFalse(BuildConfig.DEBUG);assertCapturesBlocked(true) }
}
