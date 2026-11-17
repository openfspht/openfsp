package org.openfsp.gateway.payment.api

interface ReconcilePendingPayments {
    fun reconcile(batch: Int): Int
}
