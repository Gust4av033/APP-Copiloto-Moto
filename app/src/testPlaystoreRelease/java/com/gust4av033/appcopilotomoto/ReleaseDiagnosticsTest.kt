package com.gust4av033.appcopilotomoto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.gust4av033.appcopilotomoto.presentation.CopilotUiState
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReleaseDiagnosticsTest {
    @Test fun diagnosticsAreHiddenByDefaultInPlaystoreRelease() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("ui_settings", Context.MODE_PRIVATE).edit()
            .remove("developer_diagnostics_unlocked").commit()
        assertFalse(BuildConfig.SHOW_DIAGNOSTICS_BY_DEFAULT)
        assertFalse(UserSettingsStore(context).developerDiagnosticsUnlocked())
        assertFalse(CopilotUiState().developerEnabled)
    }
}
