package org.openfsp.gateway.admin.search

import org.openfsp.gateway.admin.KeyResource
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.persistence.auth.ApiKeyEntityMapper
import org.openfsp.gateway.persistence.auth.ApiKeyJpaEntity
import org.openfsp.gateway.persistence.auth.ApiKeyJpaRepository
import org.openfsp.gateway.persistence.auth.PrincipalJpaEntity
import org.openfsp.gateway.persistence.auth.PrincipalJpaRepository
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

enum class KeyStatus { ACTIVE, EXPIRED, REVOKED, ALL }

data class KeyFilters(
    val status: KeyStatus,
    val query: String?,
)

@Component
class KeySearch(
    private val keys: ApiKeyJpaRepository,
    private val principals: PrincipalJpaRepository,
    private val mapper: ApiKeyEntityMapper,
    private val calendar: BusinessCalendar,
) {
    @Transactional(readOnly = true)
    fun search(
        filters: KeyFilters,
        page: PageQuery,
        sort: SortQuery,
    ): PageResource<KeyResource> {
        val byPrincipal = sort.field == PRINCIPAL
        val order = if (byPrincipal) Sort.unsorted() else sort.resolve(SORTABLE, CREATED)
        val found = keys.findAll(specificationOf(filters, sort.takeIf { byPrincipal }), page.toPageable(order))
        val names = principals.findAllById(found.content.map { it.principalId }.toSet()).associate { it.id to it.name }
        return found.toResource { resource(it, names[it.principalId] ?: it.principalId) }
    }

    private fun resource(
        key: ApiKeyJpaEntity,
        principal: String,
    ) = KeyResource(
        key.id,
        principal,
        "$PREFIX${key.environment}_${key.lookupPrefix}",
        mapper.scopes(key.scopes).map { it.wire }.sorted(),
        key.createdAt,
        key.expiresAt,
        key.lastUsedAt,
        key.revokedAt,
    )

    private fun specificationOf(
        filters: KeyFilters,
        principalOrder: SortQuery?,
    ): Specification<ApiKeyJpaEntity> =
        Specification { root, query, builder ->
            val principal = query.from(PrincipalJpaEntity::class.java)
            val revokedAt = root.get<Instant>("revokedAt")
            val expiresAt = root.get<Instant>("expiresAt")
            val now = calendar.now()
            val unexpired = builder.or(builder.isNull(expiresAt), builder.greaterThan(expiresAt, now))
            val prefix = builder.concat(builder.concat(builder.literal(PREFIX), root.get("environment")), builder.concat("_", root.get<String>("lookupPrefix")))
            val conditions =
                listOfNotNull(
                    builder.equal(principal.get<String>("id"), root.get<String>("principalId")),
                    when (filters.status) {
                        KeyStatus.ACTIVE -> builder.and(builder.isNull(revokedAt), unexpired)
                        KeyStatus.EXPIRED -> builder.and(builder.isNull(revokedAt), builder.not(unexpired))
                        KeyStatus.REVOKED -> builder.isNotNull(revokedAt)
                        KeyStatus.ALL -> null
                    },
                    filters.query?.let { builder.or(builder.contains(principal.get("name"), it), builder.contains(prefix, it)) },
                )
            if (principalOrder != null && query.resultType != Long::class.javaObjectType) {
                val name = principal.get<String>("name")
                query.orderBy(if (principalOrder.ascending) builder.asc(name) else builder.desc(name), builder.desc(root.get<String>("id")))
            }
            builder.and(*conditions.toTypedArray())
        }

    private companion object {
        const val PREFIX = "ofsp_"
        const val CREATED = "created"
        const val PRINCIPAL = "principal"
        val SORTABLE = mapOf(CREATED to "createdAt", "last_used" to "lastUsedAt", "status" to "revokedAt")
    }
}
