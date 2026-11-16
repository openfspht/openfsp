package org.openfsp.gateway.payments.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "payments")
class PaymentJpaEntity(
    @Id val id: String,
    val reference: String,
    var status: String,
    val amount: Long,
    val currency: String,
    val provider: String,
    var providerReference: String?,
    val providerOrderId: String,
    val payerPhoneNumber: String?,
    val description: String?,
    val returnUrl: String?,
    var nextAction: String?,
    @Column(name = "metadata") val metadataJson: String?,
    var expiresAt: Instant?,
    var completedAt: Instant?,
    var failureReason: String?,
    var failureDetail: String?,
    val createdAt: Instant,
    var updatedAt: Instant,
    val callbackTokenDigest: ByteArray?,
    val creationOutcomeUnknown: Boolean,
)
