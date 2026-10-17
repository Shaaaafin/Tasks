package dev.quiettasks.sync

import dev.quiettasks.data.SyncMode
import org.junit.Assert.*
import org.junit.Test

class SyncPolicyTest {
    @Test fun foregroundModeNeverAllowsAutomaticBackgroundSync() {
        assertTrue(maySync(SyncMode.WHILE_USING, SyncTrigger.FOREGROUND, true, false))
        assertFalse(maySync(SyncMode.WHILE_USING, SyncTrigger.FOREGROUND, false, true))
        assertFalse(maySync(SyncMode.WHILE_USING, SyncTrigger.CHARGING, false, true))
    }
    @Test fun chargingModeRequiresChargingEvenIfAJobWasAlreadyStarted() {
        assertTrue(maySync(SyncMode.CHARGING_ONLY, SyncTrigger.CHARGING, false, true))
        assertFalse(maySync(SyncMode.CHARGING_ONLY, SyncTrigger.CHARGING, true, false))
        assertFalse(maySync(SyncMode.CHARGING_ONLY, SyncTrigger.FOREGROUND, true, true))
    }
    @Test fun manualModeNeverAllowsAutomaticSync() {
        for (trigger in listOf(SyncTrigger.FOREGROUND, SyncTrigger.CHARGING)) assertFalse(maySync(SyncMode.MANUAL, trigger, true, true))
        assertTrue(maySync(SyncMode.MANUAL, SyncTrigger.MANUAL, true, false))
        assertFalse(maySync(SyncMode.MANUAL, SyncTrigger.MANUAL, false, true))
    }
}
