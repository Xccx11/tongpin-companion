package com.aster.companion.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberPolicyTest {
    @Test fun `allows an international or local dial target`() {
        assertTrue(PhoneNumberPolicy.isAllowed("+8613812345678"))
        assertTrue(PhoneNumberPolicy.isAllowed("10086"))
    }

    @Test fun `blocks control characters and non dial text`() {
        assertFalse(PhoneNumberPolicy.isAllowed("10086; rm -rf /"))
        assertFalse(PhoneNumberPolicy.isAllowed("hello"))
    }
}
