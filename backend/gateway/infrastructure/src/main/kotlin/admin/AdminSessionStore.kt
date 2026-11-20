package org.openfsp.gateway.admin

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface AdminSessionStore {
    fun open(
        id: String,
        expiresAt: Instant,
        now: Instant,
    )

    fun isOpen(
        id: String,
        now: Instant,
    ): Boolean

    fun close(id: String)
}

@Entity
@Table(name = "admin_sessions")
class AdminSessionJpaEntity(
    @Id val id: String,
    val expiresAt: Instant,
)

interface AdminSessionRepository : JpaRepository<AdminSessionJpaEntity, String> {
    fun existsByIdAndExpiresAtAfter(
        id: String,
        now: Instant,
    ): Boolean

    @Modifying
    @Query("DELETE FROM AdminSessionJpaEntity s WHERE s.expiresAt <= :now")
    fun deleteExpired(now: Instant): Int
}

@Component
class JpaAdminSessionStore(
    private val repository: AdminSessionRepository,
) : AdminSessionStore {
    @Transactional
    override fun open(
        id: String,
        expiresAt: Instant,
        now: Instant,
    ) {
        repository.deleteExpired(now)
        repository.save(AdminSessionJpaEntity(id, expiresAt))
    }

    override fun isOpen(
        id: String,
        now: Instant,
    ): Boolean = repository.existsByIdAndExpiresAtAfter(id, now)

    @Transactional
    override fun close(id: String) {
        repository.deleteById(id)
    }
}
