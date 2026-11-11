package com.aster.companion.agent

import com.aster.companion.data.ModelProfileEntity

object ImageEditRoutePolicy {
    fun usesReferenceEditEndpoint(profile: ModelProfileEntity): Boolean =
        !ComfyGatewayTaskProtocol.supports(profile)
}

/** The Comfy gateway accepts generation jobs, not OpenAI's multipart edit contract. */
class ImageEditUnsupportedException : IllegalStateException(
    "This image gateway does not support the reference-image edit endpoint"
)
