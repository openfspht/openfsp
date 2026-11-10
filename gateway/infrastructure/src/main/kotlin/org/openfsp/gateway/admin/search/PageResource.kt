package org.openfsp.gateway.admin.search

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

data class PageInfo(
    val number: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class PageResource<T>(
    val data: List<T>,
    val page: PageInfo,
)

data class SortQuery(
    val field: String?,
    val ascending: Boolean,
) {
    fun resolve(
        allowed: Map<String, String>,
        default: String,
    ): Sort {
        val property = allowed[field] ?: allowed.getValue(default)
        val direction = if (field in allowed && ascending) Sort.Direction.ASC else Sort.Direction.DESC
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"))
    }

    companion object {
        fun of(
            field: String?,
            order: String?,
        ) = SortQuery(field?.trim()?.lowercase(), order.equals("asc", ignoreCase = true))
    }
}

data class PageQuery(
    val number: Int,
    val size: Int,
) {
    fun toPageable(sort: Sort): PageRequest = PageRequest.of(number.coerceAtLeast(0), size.coerceIn(1, MAX_SIZE), sort)

    companion object {
        const val DEFAULT_SIZE = 12
        private const val MAX_SIZE = 100
    }
}

fun <E : Any, R> Page<E>.toResource(map: (E) -> R): PageResource<R> = PageResource(content.map(map), PageInfo(number, size, totalElements, totalPages))
