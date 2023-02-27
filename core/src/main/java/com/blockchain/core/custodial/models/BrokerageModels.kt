package com.blockchain.core.custodial.models

import com.blockchain.domain.paymentmethods.model.DepositTerms
import com.blockchain.domain.paymentmethods.model.SettlementReason
import com.blockchain.nabu.datamanagers.BuySellOrder
import info.blockchain.balance.CurrencyPair
import info.blockchain.balance.Money
import java.time.Duration
import java.time.ZonedDateTime

data class BrokerageQuote(
    val id: String,
    val currencyPair: CurrencyPair,
    val inputAmount: Money,
    val price: Money,
    val rawPrice: Money,
    val resultAmount: Money,
    val quoteMargin: Double?,
    val availability: Availability?,
    val settlementReason: SettlementReason?,
    val networkFee: Money,
    val staticFee: Money,
    val feeDetails: QuoteFee,
    val createdAt: ZonedDateTime,
    val expiresAt: ZonedDateTime,
    val depositTerms: DepositTerms?
) {
    fun millisToExpire(): Long {
        return Duration.between(
            ZonedDateTime.now(expiresAt.zone),
            expiresAt
        ).toMillis()
    }

    val secondsToExpire: Float
        get() = millisToExpire().div(1000f)
}

data class BuyOrderAndQuote(
    val buyOrder: BuySellOrder,
    val quote: BrokerageQuote,
)

data class QuoteFee(
    val fee: Money,
    val feeBeforePromo: Money,
    val promo: Promo,
)

enum class Promo {
    NEW_USER, NO_PROMO
}

enum class Availability {
    INSTANT, REGULAR, UNAVAILABLE
}
