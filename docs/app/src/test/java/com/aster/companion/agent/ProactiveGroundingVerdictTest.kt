package com.aster.companion.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveGroundingVerdictTest {
    @Test fun `allows only an explicit allow verdict`() {
        assertTrue(ProactiveGroundingVerdict.allows("ALLOW"))
        assertFalse(ProactiveGroundingVerdict.allows("REJECT: invented a meeting"))
    }

    @Test fun `allows an allow verdict with a concise audit explanation`() {
        assertTrue(ProactiveGroundingVerdict.allows("ALLOW\nThe message only expresses a feeling."))
    }

    @Test fun `allows an allow verdict with same line audit explanation`() {
        assertTrue(ProactiveGroundingVerdict.allows("ALLOW - the message only expresses a feeling."))
    }

    @Test fun `allows a structured allow verdict`() {
        assertTrue(ProactiveGroundingVerdict.allows("""{"verdict":"allow","citations":["recent conversation"]}"""))
    }
}
