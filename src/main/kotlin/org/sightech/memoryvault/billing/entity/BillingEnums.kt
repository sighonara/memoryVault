package org.sightech.memoryvault.billing.entity

enum class BillingInterval { MONTHLY, ANNUAL }

enum class SubscriptionStatus { ACTIVE, PAST_DUE, CANCELLED, GRACE_PERIOD }
