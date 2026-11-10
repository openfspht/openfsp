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

data class PageQuery(
    val number: Int,
    val size: Int,
) {
    fun toPageable(sort: Sort): PageRequest = PageRequest.of(number.coerceAtLeast(0), size.coerceIn(1, MAX_SIZE), sort)

    companion object {
        const val DEFAULT_SIZE = 25
        private const val MAX_SIZE = 100
    }
}

fun <E : Any, R> Page<E>.toResource(map: (E) -> R): PageResource<R> = PageResource(content.map(map), PageInfo(number, size, totalElements, totalPages))
