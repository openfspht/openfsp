package org.openfsp.gateway.shared.persistence

import org.openfsp.gateway.common.Page
import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.common.SortQuery
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

fun SortQuery.toSort(
    allowed: Map<String, String>,
    default: String,
): Sort {
    val property = allowed[field] ?: allowed.getValue(default)
    val direction = if (field in allowed && ascending) Sort.Direction.ASC else Sort.Direction.DESC
    return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"))
}

fun PageQuery.toPageable(sort: Sort): PageRequest = PageRequest.of(safeNumber, safeSize, sort)

fun <E : Any, R> org.springframework.data.domain.Page<E>.toPage(map: (E) -> R): Page<R> = Page(content.map(map), number, size, totalElements, totalPages)
