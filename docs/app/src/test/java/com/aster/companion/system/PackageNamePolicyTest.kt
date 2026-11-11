package com.aster.companion.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageNamePolicyTest {
    @Test fun `allows normal package names`() = assertTrue(PackageNamePolicy.isAllowed("com.example.calendar"))
    @Test fun `blocks sensitive and malformed package names`() {
        assertFalse(PackageNamePolicy.isAllowed("com.bank.mobile"))
        assertFalse(PackageNamePolicy.isAllowed("sh -c anything"))
    }
}
