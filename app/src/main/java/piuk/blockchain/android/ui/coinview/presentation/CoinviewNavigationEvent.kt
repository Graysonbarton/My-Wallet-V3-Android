package piuk.blockchain.android.ui.coinview.presentation

import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.CryptoAsset
import com.blockchain.coincore.StateAwareAction
import com.blockchain.commonarch.presentation.mvi_v2.NavigationEvent
import info.blockchain.balance.Money
import piuk.blockchain.android.ui.coinview.domain.model.CoinviewAccount

sealed interface CoinviewNavigationEvent : NavigationEvent {
    data class ShowAccountExplainer(
        val cvAccount: CoinviewAccount,
        val networkTicker: String,
        val interestRate: Double,
        val stakingRate: Double,
        val actions: List<StateAwareAction>
    ) : CoinviewNavigationEvent

    data class ShowAccountActions(
        val cvAccount: CoinviewAccount,
        val interestRate: Double,
        val stakingRate: Double,
        val fiatBalance: Money,
        val cryptoBalance: Money,
        val actions: List<StateAwareAction>
    ) : CoinviewNavigationEvent

    data class NavigateToBuy(
        val asset: CryptoAsset,
    ) : CoinviewNavigationEvent

    data class NavigateToSell(
        val cvAccount: CoinviewAccount
    ) : CoinviewNavigationEvent

    data class NavigateToSend(
        val cvAccount: CoinviewAccount
    ) : CoinviewNavigationEvent

    data class NavigateToReceive(
        val cvAccount: CoinviewAccount,
        val isBuyReceive: Boolean,
        val isSendReceive: Boolean
    ) : CoinviewNavigationEvent

    data class NavigateToSwap(
        val cvAccount: CoinviewAccount
    ) : CoinviewNavigationEvent

    data class NavigateToActivity(
        val cvAccount: CoinviewAccount
    ) : CoinviewNavigationEvent

    data class NavigateToInterestStatement(
        val cvAccount: CoinviewAccount
    ) : CoinviewNavigationEvent

    data class NavigateToStakingStatement(
        val cvAccount: CoinviewAccount
    ) : CoinviewNavigationEvent

    data class NavigateToInterestDeposit(
        val cvAccount: CoinviewAccount
    ) : CoinviewNavigationEvent

    data class NavigateToInterestWithdraw(
        val cvAccount: CoinviewAccount
    ) : CoinviewNavigationEvent

    data class NavigateToStakingDeposit(
        val cvAccount: CoinviewAccount
    ) : CoinviewNavigationEvent

    data class ShowNoBalanceUpsell(
        val cvAccount: CoinviewAccount,
        val action: AssetAction,
        val canBuy: Boolean
    ) : CoinviewNavigationEvent

    object ShowKycUpgrade : CoinviewNavigationEvent

    data class ShowRecurringBuyInfo(
        val recurringBuyId: String
    ) : CoinviewNavigationEvent

    data class NavigateToRecurringBuyUpsell(
        val asset: CryptoAsset
    ) : CoinviewNavigationEvent

    data class ShowRecurringBuySheet(
        val recurringBuyId: String
    ) : CoinviewNavigationEvent

    object NavigateToSupport : CoinviewNavigationEvent

    data class OpenAssetWebsite(val website: String) : CoinviewNavigationEvent
}
