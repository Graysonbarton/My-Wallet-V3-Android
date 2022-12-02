package com.blockchain.nabu.datamanagers.repositories.swap

import com.blockchain.data.FreshnessStrategy
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialOrderState
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.datamanagers.custodialwalletimpl.toCustodialOrderState
import com.blockchain.store.asSingle
import com.blockchain.store.mapData
import com.blockchain.utils.fromIso8601ToUtc
import com.blockchain.utils.toLocalTime
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Single
import java.math.BigDecimal

class CustodialRepository(
    private val pairsStore: CustodialTradingPairsStore,
    private val swapActivityStore: CustodialSwapActivityStore,
    private val assetCatalogue: AssetCatalogue
) {

    fun getSwapAvailablePairs(): Single<List<CurrencyPair>> =
        pairsStore.stream(FreshnessStrategy.Cached(true))
            .mapData { pairList ->
                pairList.mapNotNull { pair ->
                    val parts = pair.split("-")
                    if (parts.size != 2) return@mapNotNull null
                    val source = assetCatalogue.fromNetworkTicker(parts[0]) ?: return@mapNotNull null
                    val destination = assetCatalogue.fromNetworkTicker(parts[1]) ?: return@mapNotNull null
                    CurrencyPair(source, destination)
                }
            }
            .asSingle()

    fun getCustodialActivityForAsset(
        cryptoCurrency: AssetInfo,
        directions: Set<TransferDirection>
    ): Single<List<TradeTransactionItem>> =

        swapActivityStore.stream(
            FreshnessStrategy.Cached(forceRefresh = true)
        )
            .mapData { response ->
                response.mapNotNull {
                    val pair = CurrencyPair.fromRawPair(
                        it.pair, assetCatalogue
                    ) ?: return@mapNotNull null
                    val fiatCurrency =
                        assetCatalogue.fiatFromNetworkTicker(it.fiatCurrency) ?: return@mapNotNull null
                    val apiFiat = Money.fromMinor(fiatCurrency, it.fiatValue.toBigInteger())
                    val receivingValue = Money.fromMinor(pair.destination, it.priceFunnel.outputMoney.toBigInteger())
                    // priceFunnel.price comes as Major Value
                    val price = Money.fromMajor(pair.destination, BigDecimal(it.priceFunnel.price))

                    TradeTransactionItem(
                        txId = it.kind.depositTxHash ?: it.id,
                        timeStampMs = it.createdAt.fromIso8601ToUtc()?.toLocalTime()?.time
                            ?: throw java.lang.IllegalStateException("Missing timestamp or bad formatting"),
                        direction = it.kind.direction.mapToDirection(),
                        sendingAddress = it.kind.depositAddress,
                        receivingAddress = it.kind.withdrawalAddress,
                        state = it.state.toCustodialOrderState(),
                        sendingValue = Money.fromMinor(pair.source, it.priceFunnel.inputMoney.toBigInteger()),
                        receivingValue = receivingValue,
                        withdrawalNetworkFee = Money.fromMinor(
                            pair.destination,
                            it.priceFunnel.networkFee.toBigInteger()
                        ),
                        currencyPair = pair,
                        apiFiatValue = apiFiat,
                        price = price
                    )
                }.filter {
                    it.state.displayableState &&
                        it.sendingValue.currency == cryptoCurrency &&
                        directions.contains(it.direction)
                }
            }.asSingle()

    companion object {
        const val LONG_CACHE = 60000L
    }
}

private fun String.mapToDirection(): TransferDirection =
    when (this) {
        "ON_CHAIN" -> TransferDirection.ON_CHAIN // from non-custodial to non-custodial
        "FROM_USERKEY" -> TransferDirection.FROM_USERKEY // from non-custodial to custodial
        "TO_USERKEY" -> TransferDirection.TO_USERKEY // from custodial to non-custodial - not in use currently
        "INTERNAL" -> TransferDirection.INTERNAL // from custodial to custodial
        else -> throw IllegalStateException("Unknown direction to map $this")
    }

data class TradeTransactionItem(
    val txId: String,
    val timeStampMs: Long,
    val direction: TransferDirection,
    val sendingAddress: String?,
    val receivingAddress: String?,
    val state: CustodialOrderState,
    val sendingValue: Money,
    val receivingValue: Money,
    val withdrawalNetworkFee: Money,
    val currencyPair: CurrencyPair,
    val apiFiatValue: Money,
    val price: Money
)
