package org.openfsp.gateway.admin.readmodel

import org.openfsp.gateway.payments.persistence.PaymentEntityMapper
import org.openfsp.gateway.payments.persistence.PaymentJpaEntity
import org.openfsp.gateway.payments.persistence.PaymentJpaRepository
import org.openfsp.gateway.payments.resources.PaymentResource
import org.openfsp.gateway.payments.resources.PaymentResourceMapper
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class PaymentFilters(
    val account: String?,
    val status: String?,
    val provider: String?,
    val query: String?,
)

@Component
class PaymentSearch(
    private val repository: PaymentJpaRepository,
    private val entities: PaymentEntityMapper,
    private val resources: PaymentResourceMapper,
) {
    @Transactional(readOnly = true)
    fun search(
        filters: PaymentFilters,
        page: PageQuery,
        sort: SortQuery,
    ): PageResource<PaymentResource> =
        repository
            .findAll(specificationOf(filters), page.toPageable(sort.resolve(SORTABLE, CREATED)))
            .toResource { resources.toResource(entities.toDomain(it)) }

    private fun specificationOf(filters: PaymentFilters): Specification<PaymentJpaEntity> =
        Specification { root, _, builder ->
            val conditions =
                listOfNotNull(
                    filters.account?.let { builder.equal(root.get<String>("accountId"), it) },
                    filters.status?.let { builder.equal(root.get<String>("status"), it) },
                    filters.provider?.let { builder.equal(root.get<String>("provider"), it) },
                    filters.query?.let { needle ->
                        val matches =
                            listOfNotNull(
                                builder.contains(root.get("reference"), needle),
                                builder.contains(builder.coalesce(root.get("providerReference"), ""), needle),
                                builder.contains(root.get("id"), needle),
                                centsOf(needle)?.let { builder.equal(root.get<Long>("amount"), it) },
                            )
                        builder.or(*matches.toTypedArray())
                    },
                )
            builder.and(*conditions.toTypedArray())
        }

    private fun centsOf(needle: String): Long? =
        needle
            .replace(" ", "")
            .replace(",", ".")
            .takeIf(AMOUNT::matches)
            ?.let { runCatching { it.toBigDecimal().movePointRight(CENT_DIGITS).longValueExact() }.getOrNull() }

    private companion object {
        const val CREATED = "created_at"
        val SORTABLE = mapOf(CREATED to "createdAt", "reference" to "reference", "provider" to "provider", "amount" to "amount", "status" to "status")
        const val CENT_DIGITS = 2
        val AMOUNT = Regex("""^\d{1,15}(\.\d{1,2})?$""")
    }
}
