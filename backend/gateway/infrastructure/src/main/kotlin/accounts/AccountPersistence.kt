package org.openfsp.gateway.accounts

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.account.spi.MerchantAccounts
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Entity
@Table(name = "merchant_accounts")
class MerchantAccountJpaEntity(
    @Id val id: String,
    val name: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
)

interface MerchantAccountJpaRepository : JpaRepository<MerchantAccountJpaEntity, String> {
    @Modifying
    @Query(
        "insert into merchant_accounts (id, name, created_at, expires_at) values (:id, :name, :createdAt, :expiresAt) on conflict (name) do nothing",
        nativeQuery = true,
    )
    fun insertIfAbsent(
        id: String,
        name: String,
        createdAt: Instant,
        expiresAt: Instant?,
    ): Int
}

@Component
class JpaMerchantAccounts(
    private val accounts: MerchantAccountJpaRepository,
) : MerchantAccounts {
    @Transactional
    override fun insert(account: MerchantAccount): Boolean = accounts.insertIfAbsent(account.id, account.name, account.createdAt, account.expiresAt) == 1

    override fun find(id: String): MerchantAccount? = accounts.findById(id).map { it.toDomain() }.orElse(null)

    override fun all(): List<MerchantAccount> = accounts.findAll(Sort.by("createdAt", "id")).map { it.toDomain() }

    private fun MerchantAccountJpaEntity.toDomain() = MerchantAccount(id, name, createdAt, expiresAt)
}
