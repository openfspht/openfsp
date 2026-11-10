package org.openfsp.gateway.admin.search

import org.openfsp.gateway.controllers.payments.PaymentResource
import org.openfsp.gateway.controllers.payments.PaymentResourceMapper
import org.openfsp.gateway.persistence.payment.PaymentEntityMapper
import org.openfsp.gateway.persistence.payment.PaymentJpaEntity
import org.openfsp.gateway.persistence.payment.PaymentJpaRepository
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class PaymentFilters(
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
                    filters.status?.let { builder.equal(root.get<String>("status"), it) },
                    filters.provider?.let { builder.equal(root.get<String>("provider"), it) },
                    filters.query?.lowercase()?.let { needle ->
                        val pattern = "%$needle%"
                        val matches =
                            listOfNotNull(
                                builder.like(builder.lower(root.get("reference")), pattern),
                                builder.like(builder.lower(builder.coalesce(root.get("providerReference"), "")), pattern),
                                builder.like(builder.lower(root.get("id")), pattern),
                                centsOf(needle)?.let { builder.equal(root.get<Long>("amount"), it) },
                            )
                        builder.or(*matches.toTypedArray())
                    },
                )
            builder.and(*conditions.toTypedArray())
        }

    private fun centsOf(needle: String): Long? =
        needle.replace(" ", "").replace(",", ".").toBigDecimalOrNull()?.movePointRight(CENT_DIGITS)?.takeIf { it.stripTrailingZeros().scale() <= 0 }?.toLong()

    private companion object {
        const val CREATED = "created_at"
        val SORTABLE = mapOf(CREATED to "createdAt", "reference" to "reference", "provider" to "provider", "amount" to "amount", "status" to "status")
        const val CENT_DIGITS = 2
    }
}
