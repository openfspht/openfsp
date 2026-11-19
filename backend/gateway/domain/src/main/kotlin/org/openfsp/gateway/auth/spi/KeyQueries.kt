package org.openfsp.gateway.auth.spi

import org.openfsp.gateway.auth.KeyFilters
import org.openfsp.gateway.auth.KeyView
import org.openfsp.gateway.common.Page
import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.common.SortQuery
import java.time.Instant

interface KeyQueries {
    fun search(
        filters: KeyFilters,
        page: PageQuery,
        sort: SortQuery,
    ): Page<KeyView>

    fun countActive(now: Instant): Long
}
