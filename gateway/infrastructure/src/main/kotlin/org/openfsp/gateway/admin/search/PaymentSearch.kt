package org.openfsp.gateway.admin.search

import org.openfsp.gateway.controllers.payments.PaymentResource
import org.openfsp.gateway.controllers.payments.PaymentResourceMapper
import org.openfsp.gateway.persistence.payment.PaymentEntityMapper
import org.openfsp.gateway.persistence.payment.PaymentJpaEntity
import org.openfsp.gateway.persistence.payment.PaymentJpaRepository
import org.springframework.data.domain.Sort
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
    ): PageResource<PaymentResource> =
        repository
            .findAll(specificationOf(filters), page.toPageable(Sort.by(Sort.Direction.DESC, CREATED_AT)))
            .toResource { resources.toResource(entities.toDomain(it)) }

    private fun specificationOf(filters: PaymentFilters): Specification<PaymentJpaEntity> =
        Specification { root, _, builder ->
            val conditions =
                listOfNotNull(
                    filters.status?.let { builder.equal(root.get<String>("status"), it) },
                    filters.provider?.let { builder.equal(root.get<String>("provider"), it) },
                    filters.query?.lowercase()?.let { needle ->
                        val pattern = "%$needle%"
                        builder.or(
                            builder.like(builder.lower(root.get("reference")), pattern),
                            builder.like(builder.lower(builder.coalesce(root.get("providerReference"), "")), pattern),
                            builder.like(builder.lower(root.get("id")), pattern),
                        )
                    },
                )
            builder.and(*conditions.toTypedArray())
        }

    private companion object {
        const val CREATED_AT = "createdAt"
    }
}
