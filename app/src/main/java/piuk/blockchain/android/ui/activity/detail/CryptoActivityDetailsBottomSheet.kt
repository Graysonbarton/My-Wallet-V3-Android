package piuk.blockchain.android.ui.activity.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.analytics.events.ActivityAnalytics
import com.blockchain.analytics.events.LaunchOrigin
import com.blockchain.commonarch.presentation.base.HostedBottomSheet
import com.blockchain.commonarch.presentation.mvi.MviBottomSheet
import com.blockchain.componentlib.alert.BlockchainSnackbar
import com.blockchain.componentlib.alert.SnackbarType
import com.blockchain.componentlib.viewextensions.gone
import com.blockchain.componentlib.viewextensions.visible
import com.blockchain.componentlib.viewextensions.visibleIf
import com.blockchain.domain.paymentmethods.model.PaymentMethodType
import com.blockchain.earn.domain.models.interest.InterestState
import com.blockchain.earn.domain.models.staking.StakingState
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.RecurringBuyFailureReason
import com.blockchain.presentation.customviews.BlockchainListDividerDecor
import com.blockchain.presentation.koin.scopedInject
import com.google.android.material.snackbar.Snackbar
import info.blockchain.balance.AssetInfo
import info.blockchain.wallet.multiaddress.TransactionSummary
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.DialogSheetActivityDetailsBinding
import piuk.blockchain.android.simplebuy.BuySellClicked
import piuk.blockchain.android.simplebuy.SimpleBuyActivity
import piuk.blockchain.android.simplebuy.SimpleBuySyncFactory
import piuk.blockchain.android.support.SupportCentreActivity
import piuk.blockchain.android.ui.activity.ActivityType
import piuk.blockchain.android.ui.activity.detail.adapter.ActivityDetailsDelegateAdapter
import piuk.blockchain.android.ui.brokerage.BuySellFragment
import piuk.blockchain.android.ui.recurringbuy.RecurringBuyAnalytics
import piuk.blockchain.android.ui.resources.AssetResources
import piuk.blockchain.android.ui.transactionflow.analytics.DepositAnalytics
import piuk.blockchain.android.util.StringUtils

class CryptoActivityDetailsBottomSheet : MviBottomSheet<ActivityDetailsModel,
    ActivityDetailsIntents,
    ActivityDetailState,
    DialogSheetActivityDetailsBinding>() {

    interface Host : HostedBottomSheet.Host {
        fun onAddCash(currency: String)
        fun showDetailsLoadingError()
    }

    override val host: Host by lazy {
        super.host as? Host ?: throw IllegalStateException(
            "Host fragment is not a CryptoActivityDetailsBottomSheet.Host"
        )
    }

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): DialogSheetActivityDetailsBinding =
        DialogSheetActivityDetailsBinding.inflate(inflater, container, false)

    override val model: ActivityDetailsModel by scopedInject()
    private val compositeDisposable = CompositeDisposable()

    private val listAdapter: ActivityDetailsDelegateAdapter by lazy {
        ActivityDetailsDelegateAdapter(
            onActionItemClicked = { onActionItemClicked() },
            onDescriptionItemUpdated = { onDescriptionItemClicked(it) },
            onLongClick = { updateClipboard(it, requireContext()) }
        )
    }

    private val txId by lazy {
        arguments?.getString(ARG_TRANSACTION_HASH)
            ?: throw IllegalArgumentException("Transaction id should not be null")
    }

    private val asset: AssetInfo by lazy {
        arguments?.getSerializable(ARG_CRYPTO_ASSET) as? AssetInfo
            ?: throw IllegalArgumentException("Crypto asset cast failed")
    }

    private val activityType by lazy {
        arguments?.getSerializable(ARG_ACTIVITY_TYPE) as? ActivityType
            ?: throw IllegalArgumentException("ActivityDetailsType should not be null")
    }

    private lateinit var currentState: ActivityDetailState

    private val simpleBuySync: SimpleBuySyncFactory by scopedInject()

    private val assetResources: AssetResources by inject()

    override fun initControls(binding: DialogSheetActivityDetailsBinding) {
        loadActivityDetails(asset, txId, activityType)
        binding.detailsList.apply {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            addItemDecoration(BlockchainListDividerDecor(requireContext()))
            adapter = listAdapter
        }
    }

    override fun render(newState: ActivityDetailState) {
        currentState = newState
        showDescriptionUpdate(newState.descriptionState)

        binding.apply {
            title.text = if (newState.isFeeTransaction) {
                getString(R.string.activity_details_title_fee)
            } else {
                newState.transactionType?.let {
                    mapToAction(newState.transactionType)
                }
            }
            amount.text = newState.amount?.toStringWithSymbol()

            newState.transactionType?.let {
                renderCompletedPendingOrFailed(
                    newState.isPending,
                    newState.isPendingExecution,
                    newState.confirmations,
                    newState.totalConfirmations,
                    newState.transactionType,
                    newState.isFeeTransaction,
                    newState.transactionRecurringBuyState
                )

                showTransactionTypeUi(newState)
            }
        }

        if (newState.isError) {
            host.showDetailsLoadingError()
            dismiss()
        }

        if (listAdapter.items != newState.listOfItems) {
            listAdapter.items = newState.listOfItems.toList()
            listAdapter.notifyDataSetChanged()
        }
    }

    private fun showInterestUi(
        newState: ActivityDetailState
    ) {
        if (newState.isPending) {
            showStatusLabel(newState)
            showPendingPill()

            if (newState.transactionType == TransactionSummary.TransactionType.DEPOSIT) {
                showConfirmationUi(newState.confirmations, newState.totalConfirmations)
            }
        } else if (newState.interestState == InterestState.FAILED || newState.stakingState == StakingState.FAILED) {
            showFailedPill()
        } else {
            showCompletePill()
        }
    }

    private fun showStatusLabel(newState: ActivityDetailState) {
        binding.status.text = getString(
            when {
                newState.interestState != null -> {
                    when (newState.interestState) {
                        InterestState.PENDING -> R.string.activity_details_label_pending
                        InterestState.MANUAL_REVIEW -> R.string.activity_details_label_manual_review
                        InterestState.PROCESSING -> R.string.activity_details_label_processing
                        else -> R.string.empty
                    }
                }
                newState.stakingState != null -> {
                    when (newState.stakingState) {
                        StakingState.PENDING -> R.string.activity_details_label_pending
                        StakingState.MANUAL_REVIEW -> R.string.activity_details_label_manual_review
                        StakingState.PROCESSING -> R.string.activity_details_label_processing
                        else -> R.string.empty
                    }
                }
                else -> {
                    R.string.empty
                }
            }
        )
    }

    private fun showTransactionTypeUi(state: ActivityDetailState) {
        when (state.transactionType) {
            TransactionSummary.TransactionType.BUY -> showBuyUi(state)
            TransactionSummary.TransactionType.RECURRING_BUY -> showRecurringBuyUi(state)
            TransactionSummary.TransactionType.INTEREST_EARNED,
            TransactionSummary.TransactionType.DEPOSIT,
            TransactionSummary.TransactionType.WITHDRAW -> showInterestUi(state)
            else -> {
            }
        }
    }

    private fun sendAttributeRecurringBuyCancelClicked(state: ActivityDetailState) {
        val frequency =
            state.listOfItems.filterIsInstance<RecurringBuyFrequency>().firstOrNull()?.frequency
                ?: throw IllegalStateException("Missing RecurringBuyFrequency on RecurringBuy")
        val paymentMethodType = state.recurringBuyPaymentMethodType
            ?: throw IllegalStateException("Missing Input money on RecurringBuy")
        val inputMoney = state.amount
            ?: throw IllegalStateException("Missing Payment Method on RecurringBuy")

        analytics.logEvent(
            RecurringBuyAnalytics
                .RecurringBuyCancelClicked(
                    LaunchOrigin.TRANSACTION_DETAILS,
                    frequency,
                    inputMoney,
                    asset,
                    paymentMethodType
                )
        )
    }

    private fun showRecurringBuyUi(state: ActivityDetailState) {
        binding.rbSheetCancel.apply {
            visibleIf { state.recurringBuyId != null }
            binding.rbSheetCancel.setOnClickListener {
                sendAttributeRecurringBuyCancelClicked(state)

                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.settings_bank_remove_check_title)
                    .setMessage(R.string.recurring_buy_cancel_dialog_desc)
                    .setPositiveButton(R.string.common_ok) { di, _ ->
                        di.dismiss()
                        model.process(DeleteRecurringBuy)
                    }
                    .setNegativeButton(R.string.common_cancel) { di, _ ->
                        di.dismiss()
                    }.show()
            }
        }
        if (state.recurringBuyId == null) {
            BlockchainSnackbar.make(
                dialog?.window?.decorView ?: binding.root,
                getString(R.string.recurring_buy_cancelled_toast),
                type = SnackbarType.Success
            ).show()
            dismiss()
        }

        if (state.hasDeleteError) {
            BlockchainSnackbar.make(
                dialog?.window?.decorView ?: binding.root,
                getString(R.string.recurring_buy_cancelled_error_toast),
                type = SnackbarType.Error
            ).show()
        }

        if (state.recurringBuyHasFailedAndCanBeFixedByAddingFunds()) {
            binding.custodialTxButton.apply {
                visible()
                text = getString(R.string.activity_details_label_btn_cash)
                setOnClickListener {
                    state.recurringBuyOriginCurrency?.let { launchDepositFlow(it) }
                }
            }
        }

        state.recurringBuyError?.let {
            setErrorMessageAndLinks(it, state.transactionRecurringBuyState)
        }
    }

    private fun ActivityDetailState.recurringBuyHasFailedAndCanBeFixedByAddingFunds(): Boolean {
        return this.recurringBuyPaymentMethodType == PaymentMethodType.FUNDS &&
            this.recurringBuyError == RecurringBuyFailureReason.INSUFFICIENT_FUNDS &&
            this.transactionRecurringBuyState != OrderState.FAILED
    }

    private fun launchDepositFlow(originCurrency: String) {
        analytics.logEvent(DepositAnalytics.DepositClicked(LaunchOrigin.RECURRING_BUY))
        host.onAddCash(originCurrency)
    }

    private fun setErrorMessageAndLinks(
        failureReason: RecurringBuyFailureReason,
        transactionState: OrderState
    ) {

        val errorExplanation = StringUtils.getStringWithMappedAnnotations(
            requireContext(),
            toErrorMessage(failureReason, transactionState),
            emptyMap(),
            onClick = {
                requireActivity().startActivity(SupportCentreActivity.newIntent(requireContext()))
            }
        )
        binding.errorReason.apply {
            visible()
            movementMethod = LinkMovementMethod.getInstance()
            text = errorExplanation
        }
    }

    private fun showBuyUi(
        state: ActivityDetailState
    ) {
        if (state.isPending || state.isPendingExecution) {
            binding.custodialTxButton.gone()
            return
        }
        binding.custodialTxButton.text =
            getString(R.string.activity_details_buy_again)
        binding.custodialTxButton.setOnClickListener {
            analytics.logEvent(ActivityAnalytics.DETAILS_BUY_PURCHASE_AGAIN)
            compositeDisposable += simpleBuySync.performSync().onErrorComplete().observeOn(
                AndroidSchedulers.mainThread()
            )
                .subscribe {
                    analytics.logEvent(
                        BuySellClicked(
                            origin = LaunchOrigin.TRANSACTION_DETAILS,
                            type = BuySellFragment.BuySellViewType.TYPE_BUY
                        )
                    )
                    startActivity(
                        SimpleBuyActivity.newIntent(requireContext(), asset, true)
                    )
                    dismiss()
                }
        }
        binding.custodialTxButton.visible()
    }

    private fun showDescriptionUpdate(descriptionState: DescriptionState) {
        when (descriptionState) {
            DescriptionState.UPDATE_SUCCESS -> Toast.makeText(
                requireContext(),
                getString(R.string.activity_details_description_updated), Toast.LENGTH_SHORT
            ).show()
            DescriptionState.UPDATE_ERROR -> Toast.makeText(
                requireContext(),
                getString(R.string.activity_details_description_not_updated), Toast.LENGTH_SHORT
            )
                .show()
            DescriptionState.NOT_SET -> {
                // do nothing
            }
        }
    }

    private fun toErrorMessage(
        failureReason: RecurringBuyFailureReason,
        transactionState: OrderState
    ) = when (failureReason) {
        RecurringBuyFailureReason.INTERNAL_SERVER_ERROR ->
            if (transactionState.isPending()) {
                // Pending: transaction has failed but will retry after 1 hour
                R.string.recurring_buy_internal_server_error
            } else {
                R.string.recurring_buy_final_attempt_error
            }
        RecurringBuyFailureReason.INSUFFICIENT_FUNDS -> R.string.recurring_buy_insufficient_funds_error_1
        RecurringBuyFailureReason.BLOCKED_BENEFICIARY_ID -> R.string.recurring_buy_beneficiary_error
        RecurringBuyFailureReason.FAILED_BAD_FILL,
        RecurringBuyFailureReason.UNKNOWN -> R.string.recurring_buy_generic_error
    }

    private fun renderCompletedPendingOrFailed(
        pending: Boolean,
        pendingExecution: Boolean,
        confirmations: Int,
        totalConfirmations: Int?,
        transactionType: TransactionSummary.TransactionType?,
        isFeeTransaction: Boolean,
        orderState: OrderState
    ) {
        binding.apply {
            when {
                pending || pendingExecution -> {
                    showConfirmationUi(confirmations, totalConfirmations)
                    status.text = getString(
                        when {
                            transactionType == TransactionSummary.TransactionType.SENT ||
                                transactionType == TransactionSummary.TransactionType.TRANSFERRED -> {
                                analytics.logEvent(ActivityAnalytics.DETAILS_SEND_CONFIRMING)
                                R.string.activity_details_label_confirming
                            }
                            isFeeTransaction || transactionType == TransactionSummary.TransactionType.SWAP ||
                                transactionType == TransactionSummary.TransactionType.SELL -> {
                                if (isFeeTransaction) {
                                    analytics.logEvent(ActivityAnalytics.DETAILS_FEE_PENDING)
                                } else {
                                    analytics.logEvent(ActivityAnalytics.DETAILS_SWAP_PENDING)
                                }
                                R.string.activity_details_label_pending
                            }
                            transactionType == TransactionSummary.TransactionType.BUY ->
                                if (pending && !pendingExecution) {
                                    analytics.logEvent(ActivityAnalytics.DETAILS_BUY_AWAITING_FUNDS)
                                    R.string.activity_details_label_pending
                                } else {
                                    analytics.logEvent(ActivityAnalytics.DETAILS_BUY_PENDING)
                                    R.string.activity_details_label_pending_execution
                                }
                            else -> R.string.activity_details_label_confirming
                        }
                    )
                    showPendingPill()
                }
                orderState.isCancelled() -> {
                    status.text = getString(R.string.activity_details_label_cancelled)
                    showPendingPill()
                }
                totalConfirmations != null && confirmations >= totalConfirmations -> {
                    showCompletePill()
                    logAnalyticsForComplete(transactionType, isFeeTransaction)
                }
                else -> {
                    showFailedPill()
                }
            }
        }
    }

    private fun showConfirmationUi(
        confirmations: Int,
        totalConfirmations: Int?
    ) {
        if (totalConfirmations != null && totalConfirmations > 0 &&
            confirmations != totalConfirmations
        ) {
            binding.apply {
                confirmationLabel.text =
                    getString(
                        R.string.activity_details_label_confirmations,
                        confirmations.coerceAtLeast(0),
                        totalConfirmations
                    )
                confirmationProgress.setProgress(
                    (confirmations / totalConfirmations.toFloat()) * 100
                )
                confirmationLabel.visible()
                confirmationProgress.visible()
            }
        }
    }

    private fun showPendingPill() {
        binding.status.apply {
            setBackgroundResource(R.drawable.bkgd_status_unconfirmed)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.grey_800))
        }
    }

    private fun showCompletePill() {
        binding.status.apply {
            text = getString(R.string.activity_details_label_complete)
            setBackgroundResource(R.drawable.bkgd_green_100_rounded)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.green_600))
        }
    }

    private fun showFailedPill() {
        binding.status.apply {
            text = getString(R.string.activity_details_label_failed)
            setBackgroundResource(R.drawable.bkgd_red_100_rounded)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.red_600))
        }
    }

    private fun onDescriptionItemClicked(description: String) {
        model.process(
            UpdateDescriptionIntent(txId, asset, description)
        )
    }

    private fun onActionItemClicked() {
        val explorerUri = assetResources.makeBlockExplorerUrl(asset, txId)
        if (explorerUri.isNotEmpty()) {
            logAnalyticsForExplorer()
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(explorerUri)
                startActivity(this)
            }
        }
    }

    private fun updateClipboard(value: String, context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("value", value))
        BlockchainSnackbar.make(
            dialog?.window?.decorView ?: binding.root,
            getString(R.string.copied_to_clipboard),
            duration = Snackbar.LENGTH_SHORT,
            type = SnackbarType.Success
        ).show()
    }

    private fun mapToAction(transactionType: TransactionSummary.TransactionType?): String =
        when (transactionType) {
            TransactionSummary.TransactionType.TRANSFERRED -> getString(
                R.string.activity_details_title_transferred
            )
            TransactionSummary.TransactionType.RECEIVED -> getString(
                R.string.activity_details_title_received_1
            )
            TransactionSummary.TransactionType.SENT -> getString(R.string.activity_details_title_sent_1)
            TransactionSummary.TransactionType.BUY -> getString(R.string.activity_details_title_bought)
            TransactionSummary.TransactionType.SELL -> getString(
                R.string.activity_details_title_sold
            )
            TransactionSummary.TransactionType.SWAP -> getString(R.string.activity_details_title_swapped)
            TransactionSummary.TransactionType.DEPOSIT -> getString(
                R.string.activity_details_title_deposit
            )
            TransactionSummary.TransactionType.WITHDRAW -> getString(
                R.string.activity_details_title_withdraw
            )
            TransactionSummary.TransactionType.INTEREST_EARNED -> getString(
                R.string.activity_details_title_rewards_earned
            )
            TransactionSummary.TransactionType.RECURRING_BUY -> getString(
                R.string.activity_details_title_recurring_buy
            )
            else -> ""
        }

    private fun logAnalyticsForExplorer() {
        when {
            currentState.isFeeTransaction ->
                analytics.logEvent(ActivityAnalytics.DETAILS_FEE_VIEW_EXPLORER)
            currentState.transactionType == TransactionSummary.TransactionType.SENT ->
                analytics.logEvent(ActivityAnalytics.DETAILS_SEND_VIEW_EXPLORER)
            currentState.transactionType == TransactionSummary.TransactionType.SWAP ->
                analytics.logEvent(ActivityAnalytics.DETAILS_SWAP_VIEW_EXPLORER)
            currentState.transactionType == TransactionSummary.TransactionType.RECEIVED ->
                analytics.logEvent(ActivityAnalytics.DETAILS_RECEIVE_VIEW_EXPLORER)
        }
    }

    private fun logAnalyticsForComplete(
        transactionType: TransactionSummary.TransactionType?,
        isFeeTransaction: Boolean
    ) {
        when {
            isFeeTransaction ->
                analytics.logEvent(ActivityAnalytics.DETAILS_FEE_COMPLETE)
            transactionType == TransactionSummary.TransactionType.SENT ->
                analytics.logEvent(ActivityAnalytics.DETAILS_SEND_COMPLETE)
            transactionType == TransactionSummary.TransactionType.SWAP ->
                analytics.logEvent(ActivityAnalytics.DETAILS_SWAP_COMPLETE)
            transactionType == TransactionSummary.TransactionType.BUY ->
                analytics.logEvent(ActivityAnalytics.DETAILS_BUY_COMPLETE)
            transactionType == TransactionSummary.TransactionType.RECEIVED ->
                analytics.logEvent(ActivityAnalytics.DETAILS_RECEIVE_COMPLETE)
        }
    }

    private fun loadActivityDetails(
        asset: AssetInfo,
        txHash: String,
        activityType: ActivityType
    ) {
        model.process(LoadActivityDetailsIntent(asset, txHash, activityType))
    }

    override fun onDestroy() {
        model.destroy()
        super.onDestroy()
    }

    companion object {
        private const val ARG_CRYPTO_ASSET = "crypto_currency"
        private const val ARG_ACTIVITY_TYPE = "activity_type"
        private const val ARG_TRANSACTION_HASH = "tx_hash"

        fun newInstance(
            asset: AssetInfo,
            txHash: String,
            activityType: ActivityType
        ): CryptoActivityDetailsBottomSheet {
            return CryptoActivityDetailsBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CRYPTO_ASSET, asset)
                    putString(ARG_TRANSACTION_HASH, txHash)
                    putSerializable(ARG_ACTIVITY_TYPE, activityType)
                }
            }
        }
    }
}
