package piuk.blockchain.android.ui.dashboard.coinview.interstitials

import android.app.Dialog
import android.os.Bundle
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.blockchain.analytics.Analytics
import com.blockchain.analytics.events.LaunchOrigin
import com.blockchain.coincore.ActionState
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.CryptoAccount
import com.blockchain.coincore.InterestAccount
import com.blockchain.coincore.NonCustodialAccount
import com.blockchain.coincore.StakingAccount
import com.blockchain.coincore.StateAwareAction
import com.blockchain.coincore.TradingAccount
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.sheets.SheetHeader
import com.blockchain.componentlib.tablerow.DefaultTableRow
import com.blockchain.componentlib.tag.TagType
import com.blockchain.componentlib.tag.TagViewState
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.componentlib.theme.Grey300
import com.blockchain.earn.EarnAnalytics
import com.blockchain.nabu.BlockedReason
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.Currency
import info.blockchain.balance.Money
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.ui.dashboard.assetdetails.AssetDetailsAnalytics
import piuk.blockchain.android.ui.dashboard.assetdetails.assetActionEvent
import piuk.blockchain.android.ui.dashboard.coinview.CoinViewAnalytics
import piuk.blockchain.android.ui.transfer.analytics.TransferAnalyticsEvent
import piuk.blockchain.android.util.getAccount
import piuk.blockchain.android.util.putAccount

class AccountActionsBottomSheet : BottomSheetDialogFragment() {

    private val analytics: Analytics by inject()

    private val selectedAccount by lazy {
        arguments?.getAccount(SELECTED_ACCOUNT) as CryptoAccount
    }

    private val stateAwareActions by lazy { arguments?.getSerializable(STATE_AWARE_ACTIONS) as Array<StateAwareAction> }
    private val balanceFiat by lazy { arguments?.getSerializable(BALANCE_FIAT) as Money }
    private val balanceCrypto by lazy { arguments?.getSerializable(BALANCE_CRYPTO) as Money }
    private val interestRate: Double by lazy { arguments?.getDouble(INTEREST_RATE) ?: 0.0 }
    private val stakingRate: Double by lazy { arguments?.getDouble(STAKING_RATE) ?: 0.0 }

    interface Host {
        fun navigateToAction(
            action: AssetAction,
            selectedAccount: BlockchainAccount,
            assetInfo: AssetInfo
        )

        fun showUpgradeKycSheet()
        fun showSanctionsSheet(reason: BlockedReason.Sanctions)
        fun showBalanceUpsellSheet(item: AssetActionItem)
    }

    val host: Host by lazy {
        activity as? Host
            ?: throw IllegalStateException("Host activity is not a AccountActionsBottomSheet.Host")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        logEventWalletViewed(selectedAccount)
        val dialog = BottomSheetDialog(requireActivity())
        val items =
            stateAwareActions.filter { it.state != ActionState.Unavailable }
                .map { action ->
                    mapAction(
                        action,
                        selectedAccount
                    )
                }

        val assetColor = items.first().color

        dialog.setContentView(
            ComposeView(requireContext()).apply {
                setContent {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(dimensionResource(id = R.dimen.tiny_spacing))),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SheetHeader(
                            title = selectedAccount.label,
                            onClosePress = { dismiss() },
                            startImageResource = getToolbarIcon(selectedAccount, assetColor),
                            shouldShowDivider = false
                        )
                        DefaultTableRow(
                            primaryText = balanceFiat.toStringWithSymbol(),
                            secondaryText = balanceCrypto.toStringWithSymbol(),
                            endTag = when (selectedAccount) {
                                is InterestAccount -> {
                                    TagViewState(
                                        getString(R.string.actions_sheet_percentage_rate, interestRate.toString()),
                                        TagType.Success()
                                    )
                                }
                                is StakingAccount -> {
                                    TagViewState(
                                        getString(R.string.actions_sheet_percentage_rate, stakingRate.toString()),
                                        TagType.Success()
                                    )
                                }
                                else -> null
                            },
                            endImageResource = ImageResource.None,
                            onClick = { }
                        )
                        Divider(color = AppTheme.colors.light, thickness = 1.dp)
                        ActionsList(items)
                    }
                }
            }
        )

        dialog.setOnShowListener {
            val d = it as BottomSheetDialog
            val layout =
                d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout
            BottomSheetBehavior.from(layout).state = BottomSheetBehavior.STATE_EXPANDED
        }
        return dialog
    }

    private fun getToolbarIcon(selectedAccount: CryptoAccount, color: Color): ImageResource =
        when (selectedAccount) {
            is NonCustodialAccount -> ImageResource.Local(
                id = R.drawable.ic_non_custodial_explainer,
                colorFilter = ColorFilter.tint(color)
            )
            is TradingAccount -> ImageResource.Local(
                id = R.drawable.ic_custodial_explainer,
                colorFilter = ColorFilter.tint(color)
            )
            is InterestAccount -> ImageResource.Local(
                id = R.drawable.ic_rewards_explainer,
                colorFilter = ColorFilter.tint(color)
            )
            is StakingAccount -> ImageResource.Local(
                id = R.drawable.ic_staking_explainer,
                colorFilter = ColorFilter.tint(color)
            )
            else -> ImageResource.None
        }

    @Composable
    private fun ActionsList(assetActionItem: List<AssetActionItem>) {
        LazyColumn {
            items(
                items = assetActionItem,
                itemContent = {
                    ActionListItem(it)
                }
            )
        }
    }

    @Composable
    private fun ActionListItem(
        item: AssetActionItem
    ) {
        Column {
            val stateActionData = getStateActionData(item.action.state)
            val noOp = {}
            DefaultTableRow(
                startImageResource = getStartImagePerState(item.action.state, item.icon, item.asset.colour),
                primaryText = item.title,
                secondaryText = item.description,
                endImageResource = stateActionData.imageResource,
                onClick = when (stateActionData.state) {
                    ActionState.Unavailable,
                    ActionState.LockedDueToAvailability -> noOp
                    ActionState.LockedForBalance -> {
                        {
                            host.showBalanceUpsellSheet(item)
                            dismiss()
                        }
                    }
                    ActionState.LockedForTier -> {
                        {
                            host.showUpgradeKycSheet()
                            dismiss()
                        }
                    }
                    is ActionState.LockedDueToSanctions -> {
                        {
                            host.showSanctionsSheet(stateActionData.state.reason)
                            dismiss()
                        }
                    }
                    ActionState.Available -> item.actionCta
                }
            )
            Divider(color = AppTheme.colors.light, thickness = 1.dp)
        }
    }

    private fun getStartImagePerState(
        state: ActionState,
        @DrawableRes icon: Int,
        color: String
    ): ImageResource {
        return if (state != ActionState.Available) {
            ImageResource.LocalWithBackground(
                id = icon,
                backgroundColour = R.color.grey_600,
                iconTintColour = R.color.grey_600
            )
        } else {
            ImageResource.LocalWithBackgroundAndExternalResources(
                id = icon,
                backgroundColour = color,
                iconTintColour = color
            )
        }
    }

    private fun getStateActionData(state: ActionState): ActionData =
        when (state) {
            ActionState.Available -> ActionData(
                state = state,
                hasWarning = false,
                imageResource = ImageResource.Local(
                    id = com.blockchain.componentlib.R.drawable.ic_chevron_end,
                    contentDescription = null
                )
            )
            else -> ActionData(
                state = state,
                hasWarning = false,
                imageResource = ImageResource.Local(
                    R.drawable.ic_lock,
                    colorFilter = ColorFilter.tint(Grey300)
                )
            )
        }

    private fun mapAction(
        stateAwareAction: StateAwareAction,
        account: CryptoAccount
    ): AssetActionItem {
        val asset = account.currency
        return when (stateAwareAction.action) {
            AssetAction.ViewActivity ->
                AssetActionItem(
                    title = getString(R.string.activities_title),
                    icon = R.drawable.ic_tx_activity_clock,
                    description = getString(R.string.fiat_funds_detail_activity_details),
                    asset = asset,
                    action = stateAwareAction
                ) {
                    logActionEvent(AssetDetailsAnalytics.ACTIVITY_CLICKED, asset)
                    processAction(AssetAction.ViewActivity)
                }
            AssetAction.Send ->
                AssetActionItem(
                    account = account,
                    title = getString(R.string.common_send),
                    icon = R.drawable.ic_tx_sent,
                    description = getString(
                        R.string.dashboard_asset_actions_send_dsc,
                        asset.displayTicker
                    ),
                    asset = asset,
                    action = stateAwareAction
                ) {
                    // send both events, marketing uses the first event, DS uses the second one
                    logActionEvent(AssetDetailsAnalytics.SEND_CLICKED, asset)
                    analytics.logEvent(
                        TransferAnalyticsEvent.TransferClicked(
                            LaunchOrigin.CURRENCY_PAGE,
                            type = TransferAnalyticsEvent.AnalyticsTransferType.SEND
                        )
                    )
                    processAction(AssetAction.Send)
                }
            AssetAction.Receive ->
                AssetActionItem(
                    title = getString(R.string.common_receive),
                    icon = R.drawable.ic_tx_receive,
                    description = getString(
                        R.string.dashboard_asset_actions_receive_dsc,
                        asset.displayTicker
                    ),
                    asset = asset,
                    action = stateAwareAction
                ) {
                    logActionEvent(AssetDetailsAnalytics.RECEIVE_CLICKED, asset)
                    analytics.logEvent(
                        TransferAnalyticsEvent.TransferClicked(
                            LaunchOrigin.CURRENCY_PAGE,
                            type = TransferAnalyticsEvent.AnalyticsTransferType.SEND
                        )
                    )
                    processAction(AssetAction.Receive)
                }
            AssetAction.Swap -> AssetActionItem(
                account = account,
                title = getString(R.string.common_swap),
                icon = R.drawable.ic_tx_swap,
                description = getString(
                    R.string.dashboard_asset_actions_swap_dsc, asset.displayTicker
                ),
                asset = asset, action = stateAwareAction
            ) {
                logActionEvent(AssetDetailsAnalytics.SWAP_CLICKED, asset)
                processAction(AssetAction.Swap)
            }
            AssetAction.ViewStatement -> getViewStatementActionItemForAccount(asset, stateAwareAction, selectedAccount)
            AssetAction.InterestDeposit -> AssetActionItem(
                title = getString(R.string.dashboard_asset_actions_add_title),
                icon = R.drawable.ic_tx_deposit_arrow,
                description = getString(R.string.dashboard_asset_actions_add_dsc, asset.displayTicker),
                asset = asset,
                action = stateAwareAction
            ) {
                analytics.logEvent(
                    EarnAnalytics.InterestDepositClicked(
                        currency = asset.networkTicker,
                        origin = LaunchOrigin.CURRENCY_PAGE
                    )
                )
                processAction(AssetAction.InterestDeposit)
            }
            AssetAction.InterestWithdraw -> AssetActionItem(
                title = getString(R.string.common_withdraw),
                icon = R.drawable.ic_tx_withdraw,
                description = getString(R.string.dashboard_asset_actions_withdraw_dsc_1, asset.displayTicker),
                asset = asset,
                action = stateAwareAction
            ) {
                analytics.logEvent(
                    EarnAnalytics.InterestWithdrawalClicked(
                        currency = asset.networkTicker,
                        origin = LaunchOrigin.CURRENCY_PAGE
                    )
                )
                processAction(AssetAction.InterestWithdraw)
            }
            AssetAction.Sell -> AssetActionItem(
                title = getString(R.string.common_sell),
                icon = R.drawable.ic_tx_sell,
                description = getString(R.string.convert_your_crypto_to_cash),
                asset = asset,
                action = stateAwareAction
            ) {
                logActionEvent(AssetDetailsAnalytics.SELL_CLICKED, asset)
                processAction(AssetAction.Sell)
            }
            AssetAction.Buy -> AssetActionItem(
                title = getString(R.string.common_buy),
                icon = R.drawable.ic_tx_buy,
                description = getString(R.string.dashboard_asset_actions_buy_dsc, asset.displayTicker),
                asset = asset,
                action = stateAwareAction
            ) {
                processAction(AssetAction.Buy)
            }
            AssetAction.StakingDeposit -> AssetActionItem(
                title = getString(R.string.dashboard_asset_actions_add_title),
                icon = R.drawable.ic_tx_deposit_arrow,
                description = getString(R.string.dashboard_asset_actions_add_staking_dsc, asset.displayTicker),
                asset = asset,
                action = stateAwareAction
            ) {
                analytics.logEvent(
                    EarnAnalytics.StakingDepositClicked(
                        currency = asset.networkTicker,
                        origin = LaunchOrigin.CURRENCY_PAGE
                    )
                )
                processAction(AssetAction.StakingDeposit)
            }
            AssetAction.FiatWithdraw -> throw IllegalStateException("Cannot Withdraw a non-fiat currency")
            AssetAction.FiatDeposit -> throw IllegalStateException("Cannot Deposit a non-fiat currency to Fiat")
            AssetAction.Sign -> throw IllegalStateException("Sign action is not supported")
        }
    }

    private fun getViewStatementActionItemForAccount(
        asset: Currency,
        stateAwareAction: StateAwareAction,
        selectedAccount: CryptoAccount
    ): AssetActionItem {
        val title = getString(
            when (selectedAccount) {
                is InterestAccount -> R.string.dashboard_asset_actions_summary_title_1
                is StakingAccount -> R.string.dashboard_asset_actions_summary_staking_title
                else -> R.string.empty
            }
        )
        val description =
            when (selectedAccount) {
                is InterestAccount -> getString(R.string.dashboard_asset_actions_summary_dsc_1, asset.displayTicker)
                is StakingAccount -> getString(
                    R.string.dashboard_asset_actions_summary_staking_dsc, asset.displayTicker
                )
                else -> getString(R.string.empty)
            }

        val icon =
            when (selectedAccount) {
                is InterestAccount -> R.drawable.ic_tx_interest
                // TODO(dserrano) - STAKING - is this icon the one we want?
                is StakingAccount -> R.drawable.ic_tx_interest
                else -> R.drawable.ic_tx_sent
            }

        return AssetActionItem(
            title = title,
            icon = icon,
            description = description,
            asset = asset,
            action = stateAwareAction
        ) {
            processAction(AssetAction.ViewStatement)
        }
    }

    private fun processAction(action: AssetAction) {
        host.navigateToAction(
            action = action,
            selectedAccount = selectedAccount,
            assetInfo = selectedAccount.currency
        )
        dismiss()
    }

    private fun logActionEvent(event: AssetDetailsAnalytics, asset: Currency) {
        analytics.logEvent(assetActionEvent(event, asset))
    }

    private fun logEventWalletViewed(selectedAccount: CryptoAccount) {
        analytics.logEvent(
            CoinViewAnalytics.WalletsAccountsViewed(
                origin = LaunchOrigin.COIN_VIEW,
                currency = selectedAccount.currency.networkTicker,
                accountType = when (selectedAccount) {
                    is TradingAccount -> CoinViewAnalytics.Companion.AccountType.CUSTODIAL
                    is NonCustodialAccount -> CoinViewAnalytics.Companion.AccountType.USERKEY
                    is InterestAccount -> CoinViewAnalytics.Companion.AccountType.REWARDS_ACCOUNT
                    else -> CoinViewAnalytics.Companion.AccountType.EXCHANGE_ACCOUNT
                }
            )
        )
    }

    data class AssetActionItem(
        val account: BlockchainAccount?,
        val title: String,
        val icon: Int,
        val description: String,
        val asset: Currency,
        val action: StateAwareAction,
        val actionCta: () -> Unit
    ) {
        constructor(
            title: String,
            icon: Int,
            description: String,
            asset: Currency,
            action: StateAwareAction,
            actionCta: () -> Unit
        ) : this(
            null,
            title,
            icon,
            description,
            asset,
            action,
            actionCta
        )

        val color: Color
            get() = Color(android.graphics.Color.parseColor(asset.colour))
    }

    companion object {
        private const val SELECTED_ACCOUNT = "selected_account"
        private const val STATE_AWARE_ACTIONS = "state_aware_actions"
        private const val BALANCE_FIAT = "balance_fiat"
        private const val BALANCE_CRYPTO = "balance_crypto"
        private const val INTEREST_RATE = "interest_rate"
        private const val STAKING_RATE = "staking_rate"

        fun newInstance(
            selectedAccount: BlockchainAccount,
            balanceFiat: Money,
            balanceCrypto: Money,
            interestRate: Double,
            stakingRate: Double,
            stateAwareActions: Array<StateAwareAction>
        ): AccountActionsBottomSheet {
            return AccountActionsBottomSheet().apply {
                arguments = Bundle().apply {
                    putAccount(SELECTED_ACCOUNT, selectedAccount)
                    putSerializable(BALANCE_FIAT, balanceFiat)
                    putSerializable(BALANCE_CRYPTO, balanceCrypto)
                    putDouble(INTEREST_RATE, interestRate)
                    putDouble(STAKING_RATE, stakingRate)
                    putSerializable(STATE_AWARE_ACTIONS, stateAwareActions)
                }
            }
        }
    }
}

private data class ActionData(
    val state: ActionState,
    val hasWarning: Boolean,
    val imageResource: ImageResource
)
