package org.openfsp.simulator.controllers

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

fun <T> List<T>.page(
    number: Int,
    size: Int,
): PageResource<T> {
    val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
    val safeNumber = number.coerceAtLeast(0)
    val totalPages = (this.size + safeSize - 1) / safeSize
    return PageResource(drop(safeNumber * safeSize).take(safeSize), PageInfo(safeNumber, safeSize, this.size.toLong(), totalPages))
}

fun <T> List<T>.sorted(
    field: String?,
    order: String?,
    comparators: Map<String, Comparator<T>>,
    default: Comparator<T>,
): List<T> {
    val chosen = comparators[field?.lowercase()] ?: return sortedWith(default)
    return sortedWith(if (order.equals("asc", ignoreCase = true)) chosen else chosen.reversed())
}

fun String.matches(query: String?): Boolean = query.isNullOrBlank() || contains(query.trim(), ignoreCase = true)

const val DEFAULT_PAGE_SIZE = 12
private const val MAX_PAGE_SIZE = 100
