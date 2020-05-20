package piuk.blockchain.android.ui.dashboard.assetdetails

import com.jakewharton.rxrelay2.BehaviorRelay
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.balance.toFiat
import info.blockchain.wallet.prices.TimeInterval
import info.blockchain.wallet.prices.data.PriceDatum
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.AssetTokens
import piuk.blockchain.android.coincore.AvailableActions
import piuk.blockchain.android.coincore.CryptoAccountGroup
import piuk.blockchain.androidcore.data.charts.TimeSpan

data class AssetDisplayInfo(
    val cryptoValue: CryptoValue,
    val fiatValue: FiatValue,
    val actions: Set<AssetAction>,
    val interestRate: Double
)

typealias AssetDisplayMap = Map<AssetFilter, AssetDisplayInfo>

class AssetDetailsCalculator {
    // input
    val token = BehaviorRelay.create<AssetTokens>()
    val timeSpan = BehaviorRelay.createDefault<TimeSpan>(TimeSpan.MONTH)

    private val _chartLoading: BehaviorRelay<Boolean> = BehaviorRelay.createDefault<Boolean>(false)

    val chartLoading: Observable<Boolean>
        get() = _chartLoading

    val exchangeRate: Observable<String> = token.flatMapSingle {
        it.exchangeRate()
    }.map {
        it.toStringWithSymbol()
    }.subscribeOn(Schedulers.io())

    val historicPrices: Observable<List<PriceDatum>> =
        (timeSpan.distinctUntilChanged().withLatestFrom(token)
            .doOnNext { _chartLoading.accept(true) })
            .switchMapSingle { (timeSpan, token) ->
                token.historicRateSeries(timeSpan, TimeInterval.FIFTEEN_MINUTES)
                    .onErrorResumeNext(Single.just(emptyList()))
            }
            .doOnNext { _chartLoading.accept(false) }
            .subscribeOn(Schedulers.io())

    // output
    val assetDisplayDetails: Observable<AssetDisplayMap> =
        token.flatMapSingle {
            getAssetDisplayDetails(it)
        }.subscribeOn(Schedulers.computation())

    private data class Details(
        val balance: CryptoValue,
        val actions: AvailableActions
    )

    private fun Single<CryptoAccountGroup>.mapDetails(): Single<Details> =
        this.flatMap {
            it.balance.map { balance ->
                Details(balance, it.actions)
            }
        }

    private fun getAssetDisplayDetails(assetTokens: AssetTokens): Single<AssetDisplayMap> {
        return Singles.zip(
            assetTokens.exchangeRate(),
            assetTokens.accounts(AssetFilter.Total).mapDetails(),
            assetTokens.accounts(AssetFilter.Wallet).mapDetails(),
            assetTokens.accounts(AssetFilter.Custodial).mapDetails(),
            assetTokens.accounts(AssetFilter.Interest).mapDetails(),
            assetTokens.interestRate()
        ) { fiatPrice, total, nonCustodial, custodial, interest, interestRate ->
            val totalFiat = total.balance.toFiat(fiatPrice)
            val walletFiat = nonCustodial.balance.toFiat(fiatPrice)
            val custodialFiat = custodial.balance.toFiat(fiatPrice)
            val interestFiat = interest.balance.toFiat(fiatPrice)

            mutableMapOf(
                AssetFilter.Total to AssetDisplayInfo(total.balance, totalFiat, total.actions,
                    NOT_USED),
                AssetFilter.Wallet to AssetDisplayInfo(nonCustodial.balance, walletFiat,
                    nonCustodial.actions, NOT_USED)
            ).apply {
                // TODO: Going to need to filter this out, in the not-configured eventuality
                put(AssetFilter.Custodial,
                    AssetDisplayInfo(custodial.balance, custodialFiat, custodial.actions,
                        NOT_USED))

                if (interestRate != NOT_USED) {
                    put(AssetFilter.Interest,
                        AssetDisplayInfo(interest.balance, interestFiat, interest.actions,
                            interestRate))
                }
            }
        }
    }

    companion object {
        const val NOT_USED: Double = -99.0
    }
}
