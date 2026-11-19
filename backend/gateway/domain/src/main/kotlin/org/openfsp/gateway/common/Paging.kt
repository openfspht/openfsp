package org.openfsp.gateway.common

data class PageQuery(
    val number: Int,
    val size: Int,
    private val maxSize: Int = MAX_SIZE,
) {
    val safeNumber: Int get() = number.coerceAtLeast(0)
    val safeSize: Int get() = size.coerceIn(1, maxSize)

    companion object {
        const val DEFAULT_SIZE = 12
        private const val MAX_SIZE = 100
        private const val EXPORT_SIZE = 10_000

        val EXPORT = PageQuery(0, EXPORT_SIZE, EXPORT_SIZE)
    }
}

data class SortQuery(
    val field: String?,
    val ascending: Boolean,
) {
    companion object {
        fun of(
            field: String?,
            order: String?,
        ) = SortQuery(field?.trim()?.lowercase(), order.equals("asc", ignoreCase = true))
    }
}

data class Page<T>(
    val items: List<T>,
    val number: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    fun <R> map(transform: (T) -> R): Page<R> = Page(items.map(transform), number, size, totalElements, totalPages)

    companion object {
        fun <T> of(
            items: List<T>,
            page: PageQuery,
            total: Long = items.size.toLong(),
        ): Page<T> {
            val totalPages = ((total + page.safeSize - 1) / page.safeSize).toInt()
            val from = page.safeNumber * page.safeSize
            return Page(items.drop(from).take(page.safeSize), page.safeNumber, page.safeSize, total, totalPages)
        }
    }
}

fun <T> List<T>.sortedBy(
    sort: SortQuery,
    comparators: Map<String, Comparator<T>>,
    default: String,
): List<T> {
    val chosen = comparators[sort.field]
    return sortedWith(if (chosen == null) comparators.getValue(default) else if (sort.ascending) chosen else chosen.reversed())
}
