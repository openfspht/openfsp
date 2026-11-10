package org.openfsp.gateway.persistence.payment

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "payment_transitions")
class TransitionJpaEntity(
    val paymentId: String,
    val fromStatus: String?,
    val toStatus: String,
    val source: String,
    val recordedAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)

@Entity
@Table(name = "payment_conflicts")
class ConflictJpaEntity(
    val paymentId: String,
    val source: String,
    val reported: String,
    val recordedAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)

@Entity
@Table(name = "attestations")
class AttestationJpaEntity(
    val paymentId: String,
    val person: String,
    val status: String,
    val failureReason: String?,
    val evidence: String,
    val recordedAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)

@Entity
@Table(name = "indeterminate_operations")
class IndeterminateOperationJpaEntity(
    val paymentId: String,
    val operation: String,
    val error: String,
    val recordedAt: Instant,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)
