package piuk.blockchain.android.ui.home

import androidx.lifecycle.viewModelScope
import com.blockchain.coincore.AssetAction
import com.blockchain.commonarch.presentation.mvi_v2.Intent
import com.blockchain.commonarch.presentation.mvi_v2.ModelConfigArgs
import com.blockchain.commonarch.presentation.mvi_v2.ModelState
import com.blockchain.commonarch.presentation.mvi_v2.MviViewModel
import com.blockchain.commonarch.presentation.mvi_v2.NavigationEvent
import com.blockchain.commonarch.presentation.mvi_v2.ViewState
import com.blockchain.extensions.exhaustive
import com.blockchain.nabu.BlockedReason
import com.blockchain.nabu.Feature
import com.blockchain.nabu.FeatureAccess
import com.blockchain.nabu.UserIdentity
import com.blockchain.outcome.doOnSuccess
import com.blockchain.utils.awaitOutcome
import com.blockchain.walletmode.WalletMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.parcelize.Parcelize
import piuk.blockchain.android.R

class ActionsSheetViewModel(private val userIdentity: UserIdentity) : MviViewModel<
    ActionsSheetIntent,
    ActionsSheetViewState,
    ActionsSheetModelState,
    ActionsSheetNavEvent,
    ActionSheetArgs>(
    ActionsSheetModelState()
) {

    override fun viewCreated(args: ActionSheetArgs) {}

    override fun reduce(state: ActionsSheetModelState): ActionsSheetViewState {
        return with(state) {
            ActionsSheetViewState(
                actions = actions,
                bottomItem = walletMode.takeIf { it == WalletMode.NON_CUSTODIAL_ONLY }?.let {
                    SheetAction(
                        title = R.string.common_buy,
                        subtitle = R.string.top_up_trading_account,
                        icon = R.drawable.ic_menu_sheet_buy,
                        action = AssetAction.Buy,
                    )
                }
            )
        }
    }

    private suspend fun actionsForDefi(): List<SheetAction> {
        val sellEnabled = userIdentity.userAccessForFeature(Feature.Sell).map {
            it is FeatureAccess.Granted
        }.await()

        return listOfNotNull(
            SheetAction(
                title = R.string.common_swap,
                subtitle = R.string.exchange_your_crypto,
                icon = R.drawable.ic_sheet_menu_swap,
                action = AssetAction.Swap,
            ),
            SheetAction(
                title = R.string.common_sell,
                subtitle = R.string.fiat_to_cash,
                icon = R.drawable.ic_sheet_menu_sell,
                action = AssetAction.Sell,
            ).takeIf { sellEnabled },
            SheetAction(
                title = R.string.common_send,
                subtitle = R.string.transfer_to_another_wallet,
                icon = R.drawable.ic_sheet_menu_send,
                action = AssetAction.Send,
            ),
            SheetAction(
                title = R.string.common_receive,
                subtitle = R.string.receive_to_your_wallet,
                icon = R.drawable.ic_sheet_menu_receive,
                action = AssetAction.Receive,
            )
        )
    }

    private fun actionsForBrokerage(earnEnabled: Boolean): List<SheetAction> = listOfNotNull(
        SheetAction(
            title = R.string.common_buy,
            subtitle = R.string.use_your_card_or_cash,
            icon = R.drawable.ic_menu_sheet_buy,
            action = AssetAction.Buy,
        ),
        SheetAction(
            title = R.string.common_sell,
            subtitle = R.string.fiat_to_cash,
            icon = R.drawable.ic_sheet_menu_sell,
            action = AssetAction.Sell,
        ),
        SheetAction(
            title = R.string.common_swap,
            subtitle = R.string.exchange_your_crypto,
            icon = R.drawable.ic_sheet_menu_swap,
            action = AssetAction.Swap,
        ),
        SheetAction(
            title = R.string.common_send,
            subtitle = R.string.transfer_to_another_wallet,
            icon = R.drawable.ic_sheet_menu_send,
            action = AssetAction.Send,
        ),
        SheetAction(
            title = R.string.common_receive,
            subtitle = R.string.receive_to_your_wallet,
            icon = R.drawable.ic_sheet_menu_receive,
            action = AssetAction.Receive,
        ),
        if (earnEnabled) {
            null
        } else {
            SheetAction(
                title = R.string.common_rewards,
                subtitle = R.string.rewards_to_your_cryptos,
                icon = R.drawable.ic_sheet_menu_rewards,
                action = AssetAction.InterestDeposit,
            )
        }
    )

    override suspend fun handleIntent(modelState: ActionsSheetModelState, intent: ActionsSheetIntent) {
        when (intent) {
            is ActionsSheetIntent.ActionClicked -> {
                check(modelState.walletMode != null)
                handleActionForMode(modelState.walletMode, intent.action)
            }
            is ActionsSheetIntent.LoadActions -> viewModelScope.launch {
                val actions =
                    if (intent.walletMode == WalletMode.NON_CUSTODIAL_ONLY) {
                        actionsForDefi()
                    } else actionsForBrokerage(
                        intent.isEarnEnabled
                    )
                updateState {
                    it.copy(actions = actions, walletMode = intent.walletMode)
                }
            }
        }
    }

    private fun handleActionForMode(walletMode: WalletMode, action: AssetAction) {
        when (walletMode) {
            WalletMode.NON_CUSTODIAL_ONLY -> handleActionForNonCustodialMode(action)
            WalletMode.CUSTODIAL_ONLY -> handleActionForCustodialMode(action)
        }
    }

    private fun handleActionForCustodialMode(action: AssetAction) {
        when (action) {
            AssetAction.Receive -> navigate(ActionsSheetNavEvent.Receive)
            AssetAction.Send -> navigate(ActionsSheetNavEvent.Send)
            AssetAction.InterestDeposit -> navigate(ActionsSheetNavEvent.Rewards)
            AssetAction.Swap -> navigate(ActionsSheetNavEvent.Swap)
            AssetAction.Sell -> navigate(ActionsSheetNavEvent.Sell)
            AssetAction.Buy -> checkBuyStatus()
            else -> throw IllegalStateException("Action is not supported for Non custodial mode")
        }
    }

    private fun checkBuyStatus() {
        viewModelScope.launch {
            userIdentity.userAccessForFeature(Feature.Buy).awaitOutcome().doOnSuccess { accessState ->
                val blockedState = accessState as? FeatureAccess.Blocked
                blockedState?.let {
                    when (val reason = it.reason) {
                        is BlockedReason.TooManyInFlightTransactions -> navigate(
                            ActionsSheetNavEvent.TooMayPendingBuys(reason.maxTransactions)
                        )
                        is BlockedReason.NotEligible,
                        is BlockedReason.Sanctions,
                        is BlockedReason.InsufficientTier,
                        is BlockedReason.ShouldAcknowledgeStakingWithdrawal ->
                            // launch Buy anyways, because this is handled in that screen
                            navigate(
                                ActionsSheetNavEvent.Buy
                            )
                    }.exhaustive
                } ?: run {
                    navigate(
                        ActionsSheetNavEvent.Buy
                    )
                }
            }
        }
    }

    private fun handleActionForNonCustodialMode(action: AssetAction) {
        when (action) {
            AssetAction.Receive -> navigate(ActionsSheetNavEvent.Receive)
            AssetAction.Send -> navigate(ActionsSheetNavEvent.Send)
            AssetAction.Swap -> navigate(ActionsSheetNavEvent.Swap)
            AssetAction.Buy -> navigate(ActionsSheetNavEvent.TradingBuy)
            AssetAction.Sell -> navigate(ActionsSheetNavEvent.Sell)
            else -> throw IllegalStateException("Action is not supported for Non custodial mode")
        }
    }
}

data class ActionsSheetViewState(val actions: List<SheetAction>, val bottomItem: SheetAction?) : ViewState

class SheetAction(
    val icon: Int,
    val title: Int,
    val subtitle: Int,
    val action: AssetAction
)

@Parcelize
data class ActionSheetArgs(
    val walletMode: WalletMode,
) : ModelConfigArgs.ParcelableArgs

data class ActionsSheetModelState(
    val walletMode: WalletMode? = null,
    val actions: List<SheetAction> = emptyList()
) : ModelState

sealed class ActionsSheetIntent : Intent<ActionsSheetModelState> {
    class ActionClicked(val action: AssetAction) : ActionsSheetIntent()
    class LoadActions(val walletMode: WalletMode, val isEarnEnabled: Boolean) : ActionsSheetIntent()
}

sealed class ActionsSheetNavEvent : NavigationEvent {
    object Buy : ActionsSheetNavEvent()
    object TradingBuy : ActionsSheetNavEvent()
    object Sell : ActionsSheetNavEvent()
    object Receive : ActionsSheetNavEvent()
    object Send : ActionsSheetNavEvent()
    object Rewards : ActionsSheetNavEvent()
    object Swap : ActionsSheetNavEvent()
    class TooMayPendingBuys(val maxTransactions: Int) : ActionsSheetNavEvent()
}
