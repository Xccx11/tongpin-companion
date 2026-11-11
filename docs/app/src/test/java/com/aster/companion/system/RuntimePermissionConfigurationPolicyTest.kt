package com.aster.companion.system

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimePermissionConfigurationPolicyTest {
    @Test
    fun `opens system configuration when every requested permission is already granted`() {
        assertEquals(
            RuntimePermissionConfigurationAction.OPEN_APP_DETAILS,
            RuntimePermissionConfigurationPolicy.resolve(
                requestedPermissions = listOf("android.permission.CAMERA"),
                grantedPermissions = setOf("android.permission.CAMERA")
            )
        )
    }

    @Test
    fun `requests only when at least one requested permission is still missing`() {
        assertEquals(
            RuntimePermissionConfigurationAction.REQUEST_RUNTIME,
            RuntimePermissionConfigurationPolicy.resolve(
                requestedPermissions = listOf(
                    "android.permission.READ_SMS",
                    "android.permission.READ_CONTACTS"
                ),
                grantedPermissions = setOf("android.permission.READ_SMS")
            )
        )
    }
}
