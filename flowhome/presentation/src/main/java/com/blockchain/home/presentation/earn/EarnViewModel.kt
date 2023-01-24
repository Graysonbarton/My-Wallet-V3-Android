package com.blockchain.home.presentation.earn

import androidx.lifecycle.viewModelScope
import com.blockchain.coincore.AssetFilter
import com.blockchain.coincore.Coincore
import com.blockchain.coincore.CryptoAccount
import com.blockchain.coincore.InterestAccount
import com.blockchain.coincore.StakingAccount
import com.blockchain.commonarch.presentation.mvi_v2.Intent
import com.blockchain.commonarch.presentation.mvi_v2.ModelConfigArgs
import com.blockchain.commonarch.presentation.mvi_v2.ModelState
import com.blockchain.commonarch.presentation.mvi_v2.MviViewModel
import com.blockchain.commonarch.presentation.mvi_v2.NavigationEvent
import com.blockchain.commonarch.presentation.mvi_v2.ViewState
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.data.DataResource
import com.blockchain.data.FreshnessStrategy
import com.blockchain.data.RefreshStrategy
import com.blockchain.data.combineDataResources
import com.blockchain.data.dataOrElse
import com.blockchain.data.doOnData
import com.blockchain.earn.domain.service.InterestService
import com.blockchain.earn.domain.service.StakingService
import com.blockchain.extensions.minus
import com.blockchain.presentation.pulltorefresh.PullToRefresh
import com.blockchain.store.flatMapData
import com.blockchain.store.mapData
import com.blockchain.utils.CurrentTimeProvider
import com.blockchain.walletmode.WalletMode
import com.blockchain.walletmode.WalletModeService
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.Currency
import info.blockchain.balance.FiatCurrency
import info.blockchain.balance.Money
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors.toSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.asFlow

private const val MAX_SIZE = 5

class EarnViewModel(
    private val stakingService: StakingService,
    private val interestService: InterestService,
    private val coincore: Coincore,
    private val exchangeRates: ExchangeRatesDataManager,
    private val walletModeService: WalletModeService
) :
    MviViewModel<
        EarnIntent, EarnViewState, EarnModelState, EarnNavEvent, ModelConfigArgs.NoArgs>(
        initialState = EarnModelState(
            interestAssets = DataResource.Loading,
            stakingAssets = DataResource.Loading,
            stakingRates = emptyMap(),
            interestRates = emptyMap()
        )
    ) {
    private var accountsJob: Job? = null

    override fun viewCreated(args: ModelConfigArgs.NoArgs) {}

    override fun reduce(state: EarnModelState): EarnViewState {
        if (state.stakingAssets is DataResource.Data && state.interestAssets is DataResource.Data) {
            val staking = state.stakingAssets.data
            val interest = state.interestAssets.data
            val assets = (staking + interest).filter {
                it.isSmallBalance().not()
            }.take(MAX_SIZE).toSet()

            return if (assets.isEmpty()) {
                EarnViewState.NoAssetsInvested
            } else EarnViewState.Assets(
                _assets = assets,
                interestRates = state.interestRates.mapKeys { it.key.networkTicker },
                stakingRates = state.stakingRates.mapKeys { it.key.networkTicker }
            )
        }
        if (
            state.stakingAssets is DataResource.Data ||
            state.interestAssets is DataResource.Data
        ) {
            val staking = (state.stakingAssets as? DataResource.Data)?.data ?: emptySet()
            val interest = (state.interestAssets as? DataResource.Data)?.data ?: emptySet()
            val assets = (staking + interest).filter {
                it.isSmallBalance().not()
            }.take(MAX_SIZE).toSet()

            return EarnViewState.Assets(
                _assets = assets,
                interestRates = state.interestRates.mapKeys { it.key.networkTicker },
                stakingRates = state.stakingRates.mapKeys { it.key.networkTicker }
            )
        }
        return EarnViewState.None
    }

    override suspend fun handleIntent(modelState: EarnModelState, intent: EarnIntent) {
        when (intent) {
            is EarnIntent.LoadEarnAccounts -> {
                accountsJob?.cancel()
                accountsJob = viewModelScope.launch {
                    walletModeService.walletMode.collectLatest {
                        if (it == WalletMode.CUSTODIAL) {
                            loadEarnings(forceRefresh = intent.forceRefresh)
                        } else {
                            updateState { state ->
                                state.copy(
                                    interestAssets = DataResource.Error(IllegalStateException("Not available in mode")),
                                    stakingAssets = DataResource.Error(IllegalStateException("Not available in mode"))
                                )
                            }
                        }
                    }
                }
            }
            is EarnIntent.AssetSelected -> {
                coincore[intent.earnAsset.currency].accountGroup(
                    if (intent.earnAsset.type == EarnType.INTEREST)
                        AssetFilter.Interest
                    else
                        AssetFilter.Staking
                ).map {
                    val account = it.accounts.first()
                    when (intent.earnAsset.type) {
                        EarnType.STAKING -> {
                            require(account is StakingAccount)
                            EarnNavEvent.Staking(account as CryptoAccount)
                        }
                        EarnType.INTEREST -> {
                            require(account is InterestAccount)
                            EarnNavEvent.Interest(account as CryptoAccount)
                        }
                    }
                }.toObservable().asFlow().collectLatest {
                    navigate(it)
                }
            }
            EarnIntent.Refresh -> {
                updateState {
                    it.copy(lastFreshDataTime = CurrentTimeProvider.currentTimeMillis())
                }

                onIntent(EarnIntent.LoadEarnAccounts(forceRefresh = true))
            }
        }
    }

    private suspend fun loadEarnings(forceRefresh: Boolean) {
        val staking = stakingService.getBalanceForAllAssets(
            refreshStrategy = PullToRefresh.freshnessStrategy(
                shouldGetFresh = forceRefresh,
                cacheStrategy = RefreshStrategy.RefreshIfOlderThan(10, TimeUnit.MINUTES)
            )
        ).mapData {
            it.filterValues { asset -> asset.totalBalance.isPositive }
        }.doOnData {
            updateStakingAssetsIfNeeded(it.keys)
        }.flatMapData { staking ->
            val prices = staking.keys.map { asset ->
                combine(
                    exchangeRates.exchangeRateToUserFiatFlow(
                        fromAsset = asset
                    ),
                    exchangeRates.exchangeRate(
                        fromAsset = asset,
                        toAsset = FiatCurrency.Dollars
                    )
                ) { exchangeRateUserFiatData, exchangeRateUsdData ->
                    combineDataResources(
                        exchangeRateUserFiatData, exchangeRateUsdData
                    ) { exchangeRateUserFiat, exchangeRateUsd -> exchangeRateUserFiat to exchangeRateUsd }
                }.mapData { (exchangeRateUserFiat, exchangeRateUsd) ->
                    EarnAsset(
                        currency = asset,
                        type = EarnType.STAKING,
                        balance = staking[asset]?.totalBalance?.let { exchangeRateUserFiat.convert(it) }
                            ?: throw IllegalStateException("Missing asset balance"),
                        usdBalance = staking[asset]?.totalBalance?.let { exchangeRateUsd.convert(it) }
                            ?: throw IllegalStateException("Missing asset balance")
                    )
                }
            }

            prices.merge().onEach { asset ->
                updateState { state ->
                    state.addStakingAssetIfLoaded(asset)
                }
            }
        }

        val interest =
            interestService.getBalancesFlow(
                refreshStrategy =
                FreshnessStrategy.Cached(RefreshStrategy.RefreshIfOlderThan(5, TimeUnit.MINUTES))
            ).mapData {
                it.filterValues { asset -> asset.totalBalance.isPositive }
            }.doOnData {
                updateInterestAssetsIfNeeded(it.keys)
            }.flatMapData { interest ->
                val prices = interest.keys.map { asset ->
                    combine(
                        exchangeRates.exchangeRateToUserFiatFlow(
                            fromAsset = asset
                        ),
                        exchangeRates.exchangeRate(
                            fromAsset = asset,
                            toAsset = FiatCurrency.Dollars
                        )
                    ) { exchangeRateUserFiatData, exchangeRateUsdData ->
                        combineDataResources(
                            exchangeRateUserFiatData, exchangeRateUsdData
                        ) { exchangeRateUserFiat, exchangeRateUsd -> exchangeRateUserFiat to exchangeRateUsd }
                    }.mapData { (exchangeRateUserFiat, exchangeRateUsd) ->
                        EarnAsset(
                            currency = asset,
                            type = EarnType.INTEREST,
                            balance = interest[asset]?.totalBalance?.let { exchangeRateUserFiat.convert(it) }
                                ?: throw IllegalStateException("Missing asset balance"),
                            usdBalance = interest[asset]?.totalBalance?.let { exchangeRateUsd.convert(it) }
                                ?: throw IllegalStateException("Missing asset balance")
                        )
                    }
                }

                prices.merge().onEach { asset ->
                    updateState { state ->
                        state.addInterestAssetIfLoaded(asset)
                    }
                }
            }

        val interestRates = interestService.getAllInterestRates().doOnData { rates ->
            updateState {
                it.copy(
                    interestRates = rates
                )
            }
        }

        val stakingRates = stakingService.getRatesForAllAssets().doOnData { rates ->
            updateState {
                it.copy(
                    stakingRates = rates
                )
            }
        }

        merge(staking, interest, interestRates, stakingRates).collect()
    }

    private fun updateInterestAssetsIfNeeded(assets: Set<AssetInfo>) {
        val modelAssets = modelState.interestAssets.dataOrElse(emptySet()).map { it.currency.networkTicker }.toSet()
        val newAssets = assets.map { it.networkTicker }.toSet()
        if (modelAssets == newAssets)
            return
        modelAssets.forEach { asset ->
            if (asset !in newAssets) {
                updateState {
                    it.copy(
                        interestAssets = DataResource.Data(
                            it.interestAssets.dataOrElse(emptySet())
                                .minus { earnAsset -> earnAsset.currency.networkTicker == asset }
                        )
                    )
                }
            }
        }
    }

    private fun updateStakingAssetsIfNeeded(assets: Set<AssetInfo>) {
        val modelAssets = modelState.stakingAssets.dataOrElse(emptySet()).map { it.currency.networkTicker }.toSet()
        val newAssets = assets.map { it.networkTicker }.toSet()
        if (modelAssets == newAssets)
            return
        modelAssets.forEach { asset ->
            if (asset !in newAssets) {
                updateState {
                    it.copy(
                        stakingAssets = DataResource.Data(
                            it.stakingAssets.dataOrElse(emptySet())
                                .minus { earnAsset -> earnAsset.currency.networkTicker == asset }
                        )
                    )
                }
            }
        }
    }

    private fun EarnModelState.addInterestAssetIfLoaded(dataResAsset: DataResource<EarnAsset>): EarnModelState {
        val asset = dataResAsset as? DataResource.Data ?: return this
        val currentAssets = this.interestAssets.dataOrElse(emptySet())
        if (asset.data !in currentAssets) {
            val newAssets = currentAssets
                .minus { it.currency.networkTicker == asset.data.currency.networkTicker }
                .plus(asset.data)
            return this.copy(interestAssets = DataResource.Data(newAssets))
        }
        return this
    }

    private fun EarnModelState.addStakingAssetIfLoaded(dataResAsset: DataResource<EarnAsset>): EarnModelState {
        val asset = dataResAsset as? DataResource.Data ?: return this
        val currentAssets = this.stakingAssets.dataOrElse(emptySet())
        if (asset.data !in currentAssets) {
            val newAssets = currentAssets
                .minus { it.currency.networkTicker == asset.data.currency.networkTicker }
                .plus(asset.data)
            return this.copy(stakingAssets = DataResource.Data(newAssets))
        }
        return this
    }
}

data class EarnModelState(
    val interestAssets: DataResource<Set<EarnAsset>>,
    val stakingAssets: DataResource<Set<EarnAsset>>,
    val interestRates: Map<AssetInfo, Double>,
    val stakingRates: Map<AssetInfo, Double>,
    val lastFreshDataTime: Long = 0
) : ModelState

sealed interface EarnIntent : Intent<EarnModelState> {
    data class LoadEarnAccounts(
        val forceRefresh: Boolean = false
    ) : EarnIntent

    data class AssetSelected(val earnAsset: EarnAsset) : EarnIntent

    object Refresh : EarnIntent {
        override fun isValidFor(modelState: EarnModelState): Boolean {
            return PullToRefresh.canRefresh(modelState.lastFreshDataTime)
        }
    }
}

sealed class EarnViewState : ViewState {
    object None : EarnViewState()
    object NoAssetsInvested : EarnViewState()
    class Assets(
        private val _assets: Set<EarnAsset>,
        private val stakingRates: Map<String, Double>,
        private val interestRates: Map<String, Double>
    ) : EarnViewState() {
        val assets: Set<EarnAsset>
            get() = _assets.sortedWith(
                compareByDescending<EarnAsset> { it.balance }
                    .thenByDescending { it.currency.index }
            ).groupBy(
                keySelector = {
                    it.currency.networkTicker
                }
            ).mapValues {
                it.value.sortedBy { v ->
                    v.type
                }
            }.values.flatten().toSet()

        fun rateForAsset(asset: EarnAsset): Double? =
            when (asset.type) {
                EarnType.INTEREST -> interestRates[asset.currency.networkTicker]
                EarnType.STAKING -> stakingRates[asset.currency.networkTicker]
            }
    }
}

class EarnAsset(
    val currency: Currency,
    val type: EarnType,
    val balance: Money,
    val usdBalance: Money
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EarnAsset
        if (currency.networkTicker != other.currency.networkTicker) return false
        if (type != other.type) return false
        if (balance.toBigInteger() != other.balance.toBigInteger()) return false
        return true
    }

    override fun hashCode(): Int {
        var result = 17
        result = 31 * result + currency.networkTicker.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + balance.toBigInteger().hashCode()
        return result
    }
}

fun EarnAsset.isSmallBalance() = usdBalance < Money.fromMajor(FiatCurrency.Dollars, 1.toBigDecimal())

sealed class EarnNavEvent : NavigationEvent {
    class Interest(val account: CryptoAccount) : EarnNavEvent()
    class Staking(val account: CryptoAccount) : EarnNavEvent()
}

enum class EarnType {
    STAKING, INTEREST
}
