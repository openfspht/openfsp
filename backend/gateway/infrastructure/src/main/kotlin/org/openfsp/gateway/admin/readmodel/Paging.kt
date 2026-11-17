package org.openfsp.gateway.admin.readmodel

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
    private val maxSize: Int = MAX_SIZE,
) {
    val safeNumber: Int get() = number.coerceAtLeast(0)
    val safeSize: Int get() = size.coerceIn(1, maxSize)

    fun toPageable(sort: Sort): PageRequest = PageRequest.of(safeNumber, safeSize, sort)

    companion object {
        const val DEFAULT_SIZE = 12
        private const val MAX_SIZE = 100
        private const val EXPORT_SIZE = 10_000

        val EXPORT = PageQuery(0, EXPORT_SIZE, EXPORT_SIZE)
    }
}

fun <E : Any, R> Page<E>.toResource(map: (E) -> R): PageResource<R> = PageResource(content.map(map), PageInfo(number, size, totalElements, totalPages))

fun <T> pageOf(
    items: List<T>,
    page: PageQuery,
    total: Long = items.size.toLong(),
): PageResource<T> {
    val totalPages = ((total + page.safeSize - 1) / page.safeSize).toInt()
    val from = page.safeNumber * page.safeSize
    return PageResource(items.drop(from).take(page.safeSize), PageInfo(page.safeNumber, page.safeSize, total, totalPages))
}

fun <T> List<T>.sortedBy(
    sort: SortQuery,
    comparators: Map<String, Comparator<T>>,
    default: String,
): List<T> {
    val chosen = comparators[sort.field]
    return sortedWith(if (chosen == null) comparators.getValue(default) else if (sort.ascending) chosen else chosen.reversed())
}
