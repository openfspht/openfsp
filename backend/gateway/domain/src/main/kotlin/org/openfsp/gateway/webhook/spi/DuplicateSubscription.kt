package org.openfsp.gateway.webhook.spi

class DuplicateSubscription : RuntimeException("webhook name already used")
