package com.aster.companion.system

enum class RuntimePermissionConfigurationAction {
    REQUEST_RUNTIME,
    OPEN_APP_DETAILS
}

/**
 * Android does not show a permission prompt again once every requested permission is granted.
 * Route that case to the app's system permission page so a settings row never becomes a no-op.
 */
object RuntimePermissionConfigurationPolicy {
    fun resolve(
        requestedPermissions: List<String>,
        grantedPermissions: Set<String>
    ): RuntimePermissionConfigurationAction =
        if (requestedPermissions.isNotEmpty() && requestedPermissions.all(grantedPermissions::contains)) {
            RuntimePermissionConfigurationAction.OPEN_APP_DETAILS
        } else {
            RuntimePermissionConfigurationAction.REQUEST_RUNTIME
        }
}
