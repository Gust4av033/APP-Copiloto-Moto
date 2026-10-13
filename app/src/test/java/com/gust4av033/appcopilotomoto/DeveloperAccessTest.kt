package com.gust4av033.appcopilotomoto

import org.junit.Assert.*
import org.junit.Test

class DeveloperAccessTest {
    @Test fun normalBuildHasNoDeveloperAccess() { assertFalse(DeveloperAccess().canViewDiagnostics) }
    @Test fun debugStillRequiresDeliberateOptIn() { assertFalse(DeveloperAccess(debugBuild = true).canRunExperiments) }
    @Test fun releaseOptInIsReadOnly() { assertTrue(DeveloperAccess(true, false).canViewDiagnostics); assertFalse(DeveloperAccess(true, false).canRunExperiments) }
    @Test fun debugOptInAllowsExperiments() { assertTrue(DeveloperAccess(true, true).canRunExperiments) }
    @Test fun sixTapsCannotConfirm() { val c=DeveloperAccessChallenge(); repeat(6) { assertFalse(c.tap(it * 200L)) }; assertFalse(c.confirm()) }
    @Test fun sevenTapsRequireConfirmationAndCannotBeReused() { val c=DeveloperAccessChallenge(); repeat(6) { c.tap(it*200L) }; assertTrue(c.tap(1200)); assertTrue(c.confirm()); assertFalse(c.confirm()) }
    @Test fun slowTapsDoNotAccumulate() { val c=DeveloperAccessChallenge(); repeat(14) { assertFalse(c.tap(it*3000L)) }; assertFalse(c.confirm()) }
    @Test fun cancellationDiscardsChallenge() { val c=DeveloperAccessChallenge(); repeat(7) { c.tap(it*200L) }; c.reset(); assertFalse(c.confirm()) }
    @Test fun clockGoingBackResetsChallenge() { val c=DeveloperAccessChallenge(); repeat(6) { c.tap(10000+it*200L) }; assertFalse(c.tap(50)); assertFalse(c.confirm()) }
    @Test fun sensitivityPreservesEstablishedDefault() { assertEquals(SensitivityPreset.BALANCED, SensitivityPreset.closest(80f)); assertEquals(80f, SensitivityPreset.BALANCED.value, 0f) }
}
