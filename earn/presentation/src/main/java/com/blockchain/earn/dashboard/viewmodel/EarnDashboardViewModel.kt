package com.blockchain.earn.dashboard.viewmodel

import androidx.lifecycle.viewModelScope
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.AssetFilter
import com.blockchain.coincore.Coincore
import com.blockchain.coincore.CryptoAccount
import com.blockchain.coincore.toUserFiat
import com.blockchain.commonarch.presentation.mvi_v2.ModelConfigArgs
import com.blockchain.commonarch.presentation.mvi_v2.MviViewModel
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.data.DataResource
import com.blockchain.data.FreshnessStrategy
import com.blockchain.data.combineDataResources
import com.blockchain.data.doOnData
import com.blockchain.domain.eligibility.model.StakingEligibility
import com.blockchain.earn.domain.models.interest.InterestAccountBalance
import com.blockchain.earn.domain.models.interest.InterestEligibility
import com.blockchain.earn.domain.models.staking.StakingAccountBalance
import com.blockchain.earn.domain.service.InterestService
import com.blockchain.earn.domain.service.StakingService
import com.blockchain.nabu.BlockedReason
import com.blockchain.nabu.Feature
import com.blockchain.nabu.FeatureAccess
import com.blockchain.nabu.UserIdentity
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.store.filterNotLoading
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.Money
import java.math.BigDecimal
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.awaitSingle
import kotlinx.coroutines.rx3.awaitSingleOrNull
import timber.log.Timber

class EarnDashboardViewModel(
    private val coincore: Coincore,
    private val stakingService: StakingService,
    private val interestService: InterestService,
    private val exchangeRatesDataManager: ExchangeRatesDataManager,
    private val userIdentity: UserIdentity,
    private val assetCatalogue: AssetCatalogue,
    private val custodialWalletManager: CustodialWalletManager
) : MviViewModel<EarnDashboardIntent,
    EarnDashboardViewState,
    EarnDashboardModelState,
    EarnDashboardNavigationEvent,
    ModelConfigArgs.NoArgs
    >(initialState = EarnDashboardModelState()) {

    override fun viewCreated(args: ModelConfigArgs.NoArgs) {
        viewModelScope.launch {
            onIntent(EarnDashboardIntent.LoadEarn)
        }
    }

    override fun reduce(state: EarnDashboardModelState): EarnDashboardViewState = state.run {
        EarnDashboardViewState(
            dashboardState = reduceDashboardState(
                isLoading = isLoading,
                error = error,
                earnData = earnData,
                earningTabFilterBy = earningTabFilterBy,
                earningTabQueryBy = earningTabQueryBy,
                discoverTabFilterBy = discoverTabFilterBy,
                discoverTabQueryBy = discoverTabQueryBy
            ),
            earningTabFilterBy = earningTabFilterBy,
            earningTabQueryBy = earningTabQueryBy,
            discoverTabFilterBy = discoverTabFilterBy,
            discoverTabQueryBy = discoverTabQueryBy
        )
    }

    override suspend fun handleIntent(modelState: EarnDashboardModelState, intent: EarnDashboardIntent) =
        when (intent) {
            is EarnDashboardIntent.LoadEarn -> loadEarn()
            is EarnDashboardIntent.LoadSilently -> collectEarnData(false)
            is EarnDashboardIntent.UpdateEarningTabListFilter -> updateState {
                it.copy(
                    earningTabFilterBy = intent.filter
                )
            }
            is EarnDashboardIntent.UpdateEarningTabSearchQuery -> updateState {
                it.copy(
                    earningTabQueryBy = intent.searchTerm
                )
            }
            is EarnDashboardIntent.UpdateDiscoverTabListFilter -> updateState {
                it.copy(
                    discoverTabFilterBy = intent.filter
                )
            }
            is EarnDashboardIntent.UpdateDiscoverTabSearchQuery -> updateState {
                it.copy(
                    discoverTabQueryBy = intent.searchTerm
                )
            }
            is EarnDashboardIntent.DiscoverItemSelected -> {
                when (intent.earnAsset.eligibility) {
                    EarnEligibility.Eligible -> showAcquireOrSummaryForEarnType(
                        earnType = intent.earnAsset.type,
                        assetTicker = intent.earnAsset.assetTicker
                    )
                    is EarnEligibility.NotEligible -> navigate(
                        EarnDashboardNavigationEvent.OpenBlockedForRegionSheet(intent.earnAsset.type)
                    )
                }
            }
            is EarnDashboardIntent.EarningItemSelected ->
                showAcquireOrSummaryForEarnType(
                    earnType = intent.earnAsset.type,
                    assetTicker = intent.earnAsset.assetTicker
                )
            is EarnDashboardIntent.CarouselLearnMoreSelected ->
                navigate(EarnDashboardNavigationEvent.OpenUrl(intent.url))
            is EarnDashboardIntent.OnNavigateToAction -> {
                when (intent.action) {
                    AssetAction.Buy -> navigate(EarnDashboardNavigationEvent.OpenBuy(intent.assetInfo))
                    AssetAction.Receive -> navigate(
                        EarnDashboardNavigationEvent.OpenReceive(intent.assetInfo.networkTicker)
                    )
                    else -> throw IllegalStateException("Earn dashboard: ${intent.action} not valid for navigation")
                }
            }
        }

    private fun reduceDashboardState(
        isLoading: Boolean,
        error: EarnDashboardError,
        earnData: CombinedEarnData?,
        earningTabFilterBy: EarnDashboardListFilter,
        earningTabQueryBy: String,
        discoverTabFilterBy: EarnDashboardListFilter,
        discoverTabQueryBy: String
    ): DashboardState =
        when {
            isLoading -> DashboardState.Loading
            error != EarnDashboardError.None -> DashboardState.ShowError(error)
            else -> earnData?.let { data ->
                val hasStakingBalance = data.stakingBalances.values.any { it.totalBalance.isPositive }
                val hasInterestBalance = data.interestBalances.values.any { it.totalBalance.isPositive }

                if (data.interestFeatureAccess !is FeatureAccess.Granted && !hasInterestBalance &&
                    data.stakingFeatureAccess !is FeatureAccess.Granted && !hasStakingBalance
                ) {
                    return DashboardState.ShowKyc
                }

                return if (hasStakingBalance || hasInterestBalance) {
                    splitEarningAndDiscoverData(
                        data = data,
                        earningTabFilterBy = earningTabFilterBy,
                        earningTabQueryBy = earningTabQueryBy,
                        discoverTabFilterBy = discoverTabFilterBy,
                        discoverTabQueryBy = discoverTabQueryBy
                    )
                } else {
                    buildDiscoverList(
                        data = data,
                        discoverTabFilterBy = discoverTabFilterBy,
                        discoverTabQueryBy = discoverTabQueryBy
                    )
                }
            } ?: DashboardState.Loading
        }

    private suspend fun showAcquireOrSummaryForEarnType(earnType: EarnType, assetTicker: String) {
        assetCatalogue.fromNetworkTicker(assetTicker)?.let { currency ->
            val tradingAccount = coincore[currency].accountGroup(AssetFilter.Trading).awaitSingle().accounts.first()
            val pkwAccountsBalance =
                coincore[currency].accountGroup(AssetFilter.NonCustodial).awaitSingleOrNull()?.accounts?.map {
                    it.balance.firstOrNull()
                }?.toList()?.sumOf { it?.total?.toBigDecimal() ?: BigDecimal.ZERO } ?: Money.zero(currency)
                    .toBigDecimal()

            if (tradingAccount.balance.firstOrNull()?.total?.isPositive == true ||
                pkwAccountsBalance > BigDecimal.ZERO
            ) {
                when (earnType) {
                    EarnType.Passive -> {
                        getAccountForInterest(currency.networkTicker)
                    }
                    EarnType.Staking -> navigate(
                        EarnDashboardNavigationEvent.OpenStakingSummarySheet(currency.networkTicker)
                    )
                }
            } else {
                custodialWalletManager.isCurrencyAvailableForTrading(
                    assetInfo = currency as AssetInfo,
                    freshnessStrategy = FreshnessStrategy.Cached(forceRefresh = false)
                ).filterNotLoading()
                    .doOnData { availableToBuy ->
                        navigate(
                            EarnDashboardNavigationEvent.OpenBuyOrReceiveSheet(
                                when (earnType) {
                                    EarnType.Passive -> AssetAction.InterestDeposit
                                    EarnType.Staking -> AssetAction.StakingDeposit
                                },
                                availableToBuy, tradingAccount
                            )
                        )
                    }.firstOrNull()
            }
        } ?: Timber.e("Earn Dashboard - unknown asset $assetTicker")
    }

    private suspend fun getAccountForInterest(assetTicker: String) {
        assetCatalogue.fromNetworkTicker(assetTicker)?.let {
            navigate(
                EarnDashboardNavigationEvent.OpenRewardsSummarySheet(
                    coincore[it].accountGroup(AssetFilter.Interest).awaitSingle().accounts.first() as CryptoAccount
                )
            )
        }
    }

    private fun buildDiscoverList(
        data: CombinedEarnData,
        discoverTabFilterBy: EarnDashboardListFilter,
        discoverTabQueryBy: String
    ): DashboardState.OnlyDiscover {
        val discoverList = mutableListOf<EarnAsset>()
        data.stakingEligibility.map { (asset, eligibility) ->
            val balance = data.stakingBalances[asset]?.totalBalance ?: Money.zero(asset)
            discoverList.add(
                asset.createStakingAsset(
                    balance = balance,
                    stakingRate = data.stakingRates[asset],
                    eligibility = eligibility
                )
            )
        }

        data.interestEligibility.map { (asset, eligibility) ->
            val balance = data.interestBalances[asset]?.totalBalance ?: Money.zero(asset)
            discoverList.add(
                asset.createPassiveAsset(
                    balance = balance,
                    passiveRate = data.interestRates[asset],
                    eligibility = eligibility
                )
            )
        }

        return DashboardState.OnlyDiscover(
            discoverList.sortListByFilterAndQuery(discoverTabFilterBy, discoverTabQueryBy).sortByRate()
        )
    }

    private fun splitEarningAndDiscoverData(
        data: CombinedEarnData,
        earningTabFilterBy: EarnDashboardListFilter,
        earningTabQueryBy: String,
        discoverTabFilterBy: EarnDashboardListFilter,
        discoverTabQueryBy: String
    ): DashboardState.EarningAndDiscover {
        val earningList = mutableListOf<EarnAsset>()
        val discoverList = mutableListOf<EarnAsset>()

        data.stakingEligibility.map { (asset, eligibility) ->
            val balance = data.stakingBalances[asset]?.totalBalance ?: Money.zero(asset)
            if (balance.isPositive) {
                earningList.add(
                    asset.createStakingAsset(
                        balance = balance,
                        stakingRate = data.stakingRates[asset],
                        eligibility = eligibility
                    )
                )
            } else {
                discoverList.add(
                    asset.createStakingAsset(
                        balance = balance,
                        stakingRate = data.stakingRates[asset],
                        eligibility = eligibility
                    )
                )
            }
        }

        data.interestEligibility.map { (asset, eligibility) ->
            val balance = data.interestBalances[asset]?.totalBalance ?: Money.zero(asset)

            if (balance.isPositive) {
                earningList.add(
                    asset.createPassiveAsset(
                        balance = balance,
                        passiveRate = data.interestRates[asset],
                        eligibility = eligibility
                    )
                )
            } else {
                discoverList.add(
                    asset.createPassiveAsset(
                        balance = balance,
                        passiveRate = data.interestRates[asset],
                        eligibility = eligibility
                    )
                )
            }
        }

        return DashboardState.EarningAndDiscover(
            earningList.sortListByFilterAndQuery(earningTabFilterBy, earningTabQueryBy).sortByBalance(),
            discoverList.sortListByFilterAndQuery(discoverTabFilterBy, discoverTabQueryBy).sortByRate()
        )
    }

    private fun AssetInfo.createStakingAsset(balance: Money, stakingRate: Double?, eligibility: StakingEligibility) =
        EarnAsset(
            assetTicker = networkTicker,
            assetName = name,
            iconUrl = logo,
            rate = stakingRate ?: 0.0,
            eligibility = eligibility.toEarnEligibility(),
            balanceCrypto = balance,
            balanceFiat = balance.toUserFiat(exchangeRatesDataManager),
            type = EarnType.Staking
        )

    private fun AssetInfo.createPassiveAsset(balance: Money, passiveRate: Double?, eligibility: InterestEligibility) =
        EarnAsset(
            assetTicker = networkTicker,
            assetName = name,
            iconUrl = logo,
            rate = passiveRate ?: 0.0,
            eligibility = eligibility.toEarnEligibility(),
            balanceCrypto = balance,
            balanceFiat = balance.toUserFiat(exchangeRatesDataManager),
            type = EarnType.Passive
        )

    private fun List<EarnAsset>.sortListByFilterAndQuery(
        filter: EarnDashboardListFilter,
        query: String
    ): List<EarnAsset> =
        when (filter) {
            EarnDashboardListFilter.All -> this
            EarnDashboardListFilter.Staking -> this.filter { it.type == EarnType.Staking }
            EarnDashboardListFilter.Rewards -> this.filter { it.type == EarnType.Passive }
        }.filter {
            query.isEmpty() || it.assetName.contains(query, true) ||
                it.assetTicker.contains(query, true)
        }

    private fun List<EarnAsset>.sortByRate(): List<EarnAsset> =
        this.sortedWith(
            compareByDescending<EarnAsset> { it.eligibility is EarnEligibility.Eligible }.thenByDescending { it.rate }
        )

    private fun List<EarnAsset>.sortByBalance(): List<EarnAsset> =
        this.sortedByDescending { it.balanceFiat }

    private suspend fun loadEarn() {
        updateState {
            it.copy(
                isLoading = true
            )
        }

        collectEarnData(true)
    }

    private suspend fun collectEarnData(showLoading: Boolean) {
        val accessMap = try {
            userIdentity.userAccessForFeatures(
                listOf(Feature.DepositStaking, Feature.DepositInterest)
            ).await()
        } catch (e: Exception) {
            mapOf(
                Feature.DepositStaking to FeatureAccess.Blocked(BlockedReason.NotEligible("")),
                Feature.DepositInterest to FeatureAccess.Blocked(BlockedReason.NotEligible(""))
            )
        }

        combine(
            stakingService.getBalanceForAllAssets(),
            stakingService.getEligibilityForAssets(),
            stakingService.getRatesForAllAssets(),
            interestService.getBalancesFlow(),
            interestService.getEligibilityForAssets(),
            interestService.getAllInterestRates(),
        ) { listOfData ->
            require(listOfData.size == 6)
            combineDataResources(
                listOfData.toList()
            ) { data ->
                CombinedEarnData(
                    stakingBalances = data[0] as Map<AssetInfo, StakingAccountBalance>,
                    stakingEligibility = data[1] as Map<AssetInfo, StakingEligibility>,
                    stakingRates = data[2] as Map<AssetInfo, Double>,
                    interestBalances = data[3] as Map<AssetInfo, InterestAccountBalance>,
                    interestEligibility = data[4] as Map<AssetInfo, InterestEligibility>,
                    interestRates = data[5] as Map<AssetInfo, Double>,
                    interestFeatureAccess = accessMap[Feature.DepositInterest]!!,
                    stakingFeatureAccess = accessMap[Feature.DepositStaking]!!
                )
            }
        }.collectLatest { data ->
            when (data) {
                is DataResource.Data -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            earnData = data.data
                        )
                    }
                }
                is DataResource.Error -> {
                    updateState {
                        it.copy(
                            isLoading = false,
                            error = EarnDashboardError.DataFetchFailed
                        )
                    }
                }
                DataResource.Loading -> {
                    updateState {
                        it.copy(isLoading = showLoading)
                    }
                }
            }
        }
    }

    private fun StakingEligibility.toEarnEligibility(): EarnEligibility =
        if (this is StakingEligibility.Eligible) {
            EarnEligibility.Eligible
        } else {
            when (this as StakingEligibility.Ineligible) {
                StakingEligibility.Ineligible.REGION -> EarnEligibility.NotEligible(EarnIneligibleReason.REGION)
                StakingEligibility.Ineligible.KYC_TIER -> EarnEligibility.NotEligible(EarnIneligibleReason.KYC_TIER)
                StakingEligibility.Ineligible.OTHER -> EarnEligibility.NotEligible(EarnIneligibleReason.OTHER)
                StakingEligibility.Ineligible.NONE -> EarnEligibility.NotEligible(EarnIneligibleReason.OTHER)
            }
        }

    private fun InterestEligibility.toEarnEligibility(): EarnEligibility =
        if (this is InterestEligibility.Eligible) {
            EarnEligibility.Eligible
        } else {
            when (this as InterestEligibility.Ineligible) {
                InterestEligibility.Ineligible.REGION -> EarnEligibility.NotEligible(EarnIneligibleReason.REGION)
                InterestEligibility.Ineligible.KYC_TIER -> EarnEligibility.NotEligible(EarnIneligibleReason.KYC_TIER)
                InterestEligibility.Ineligible.OTHER -> EarnEligibility.NotEligible(EarnIneligibleReason.OTHER)
                InterestEligibility.Ineligible.NONE -> EarnEligibility.NotEligible(EarnIneligibleReason.OTHER)
            }
        }
}
