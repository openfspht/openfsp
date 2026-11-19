package org.openfsp.gateway.shared.persistence

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate

private const val ESCAPE = '\\'
private val WILDCARDS = Regex("""[\\%_]""")

fun CriteriaBuilder.contains(
    field: Expression<String>,
    needle: String,
): Predicate = like(lower(field), "%${needle.lowercase().replace(WILDCARDS) { "$ESCAPE${it.value}" }}%", ESCAPE)
