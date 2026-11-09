package org.openfsp.protocol

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class NextAction(
    val type: Type,
    @get:JsonProperty("redirect_url") val redirectUrl: String? = null,
    @get:JsonProperty("expires_at") val expiresAt: Instant? = null,
) {
    init {
        require((type == Type.REDIRECT) == (redirectUrl != null)) { "redirect_url is required for, and only for, redirect" }
    }

    enum class Type(
        @get:JsonValue val wire: String,
    ) {
        REDIRECT("redirect"),
        PAYER_APPROVAL("payer_approval"),
        NONE("none"),
    }

    companion object {
        fun redirect(
            url: String,
            expiresAt: Instant?,
        ) = NextAction(Type.REDIRECT, url, expiresAt)

        val payerApproval = NextAction(Type.PAYER_APPROVAL)
    }
}
