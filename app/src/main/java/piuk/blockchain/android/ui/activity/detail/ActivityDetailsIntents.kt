package piuk.blockchain.android.ui.activity.detail

import com.blockchain.coincore.CustodialInterestActivitySummaryItem
import com.blockchain.coincore.CustodialStakingActivitySummaryItem
import com.blockchain.coincore.CustodialTradingActivitySummaryItem
import com.blockchain.coincore.CustodialTransferActivitySummaryItem
import com.blockchain.coincore.NonCustodialActivitySummaryItem
import com.blockchain.coincore.RecurringBuyActivitySummaryItem
import com.blockchain.coincore.TradeActivitySummaryItem
import com.blockchain.commonarch.presentation.mvi.MviIntent
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.TransactionType
import com.blockchain.nabu.datamanagers.custodialwalletimpl.OrderType
import info.blockchain.balance.AssetInfo
import info.blockchain.wallet.multiaddress.TransactionSummary
import java.util.Date
import piuk.blockchain.android.ui.activity.ActivityType

sealed class ActivityDetailsIntents : MviIntent<ActivityDetailState>

class LoadActivityDetailsIntent(
    val asset: AssetInfo,
    val txHash: String,
    val activityType: ActivityType
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState
    }
}

class LoadNonCustodialCreationDateIntent(
    val summaryItem: NonCustodialActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState
    }
}

object ActivityDetailsLoadFailedIntent : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            isError = true
        )
    }
}

class LoadNonCustodialHeaderDataIntent(
    private val summaryItem: NonCustodialActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            transactionType = summaryItem.transactionType,
            amount = summaryItem.value,
            isPending = !summaryItem.isConfirmed,
            isFeeTransaction = summaryItem.isFeeTransaction,
            confirmations = summaryItem.confirmations,
            totalConfirmations = (summaryItem.currency as AssetInfo).requiredConfirmations
        )
    }
}

class LoadCustodialTradingHeaderDataIntent(
    private val summaryItem: CustodialTradingActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            transactionType = if (summaryItem.type == OrderType.BUY) TransactionSummary.TransactionType.BUY else
                TransactionSummary.TransactionType.SELL,
            amount = summaryItem.value,
            isPending = summaryItem.state == OrderState.AWAITING_FUNDS,
            isPendingExecution = summaryItem.state == OrderState.PENDING_EXECUTION,
            isFeeTransaction = false,
            confirmations = 0,
            totalConfirmations = if (summaryItem.state.isFinished()) 0 else null
        )
    }
}

class LoadRecurringBuyDetailsHeaderDataIntent(
    private val recurringBuyItem: RecurringBuyActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            recurringBuyId = recurringBuyItem.recurringBuyId,
            transactionType = TransactionSummary.TransactionType.RECURRING_BUY,
            amount = if (recurringBuyItem.value.isPositive) {
                recurringBuyItem.value
            } else {
                recurringBuyItem.fundedFiat
            },
            isPending = recurringBuyItem.transactionState.isPending(),
            isPendingExecution = recurringBuyItem.transactionState == OrderState.PENDING_EXECUTION,
            isError = recurringBuyItem.transactionState.hasFailed(),
            isFeeTransaction = false,
            confirmations = 0,
            totalConfirmations = if (recurringBuyItem.transactionState.isFinished()) 0 else null,
            recurringBuyError = recurringBuyItem.failureReason,
            transactionRecurringBuyState = recurringBuyItem.transactionState,
            recurringBuyPaymentMethodType = recurringBuyItem.paymentMethodType,
            recurringBuyOriginCurrency = recurringBuyItem.fundedFiat.currencyCode
        )
    }
}

object DeleteRecurringBuy : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState = oldState
}

object RecurringBuyDeletedSuccessfully : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState =
        oldState.copy(
            recurringBuyId = null,
            hasDeleteError = false,
            isError = false
        )
}

object RecurringBuyDeleteError : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState =
        oldState.copy(
            hasDeleteError = true
        )
}

class LoadCustodialInterestHeaderDataIntent(
    private val summaryItem: CustodialInterestActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            transactionType = summaryItem.type,
            interestState = summaryItem.state,
            amount = summaryItem.value,
            isPending = summaryItem.isPending(),
            isFeeTransaction = false,
            confirmations = summaryItem.confirmations,
            totalConfirmations = summaryItem.account.currency.requiredConfirmations
        )
    }
}

class LoadCustodialStakingHeaderDataIntent(
    private val summaryItem: CustodialStakingActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            transactionType = summaryItem.type,
            stakingState = summaryItem.state,
            amount = summaryItem.value,
            isPending = summaryItem.isPending(),
            isFeeTransaction = false,
            confirmations = summaryItem.confirmations,
            totalConfirmations = summaryItem.account.currency.requiredConfirmations
        )
    }
}

class LoadCustodialSendHeaderDataIntent(
    private val summaryItem: CustodialTransferActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            transactionType = if (summaryItem.type == TransactionType.DEPOSIT) {
                TransactionSummary.TransactionType.RECEIVED
            } else {
                TransactionSummary.TransactionType.SENT
            },
            amount = summaryItem.value,
            isPending = !summaryItem.isConfirmed,
            isFeeTransaction = false,
            confirmations = 0,
            totalConfirmations = 0
        )
    }
}

class LoadSwapHeaderDataIntent(
    private val summaryItem: TradeActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            transactionType = TransactionSummary.TransactionType.SWAP,
            amount = summaryItem.value,
            isPending = summaryItem.state.isPending,
            isFeeTransaction = false,
            totalConfirmations = 0,
            confirmations = 0
        )
    }
}

class LoadSellHeaderDataIntent(
    private val summaryItem: TradeActivitySummaryItem
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            transactionType = TransactionSummary.TransactionType.SELL,
            amount = summaryItem.receivingValue,
            isPending = summaryItem.state.isPending,
            isFeeTransaction = false,
            totalConfirmations = 0,
            confirmations = 0
        )
    }
}

class ListItemsLoadedIntent(
    private val list: List<ActivityDetailsType>
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        val currentList = oldState.listOfItems.toMutableSet()
        currentList.addAll(list.toSet())
        return oldState.copy(
            listOfItems = currentList,
            descriptionState = DescriptionState.NOT_SET
        )
    }
}

object ListItemsFailedToLoadIntent : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            isError = true
        )
    }
}

object CreationDateLoadFailedIntent : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            isError = true
        )
    }
}

object DescriptionUpdatedIntent : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            descriptionState = DescriptionState.UPDATE_SUCCESS
        )
    }
}

object DescriptionUpdateFailedIntent : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState.copy(
            descriptionState = DescriptionState.UPDATE_ERROR
        )
    }
}

class CreationDateLoadedIntent(private val createdDate: Date) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        val list = oldState.listOfItems.toMutableSet()
        list.add(Created(createdDate))
        return oldState.copy(
            listOfItems = list
        )
    }
}

class UpdateDescriptionIntent(
    val txId: String,
    val asset: AssetInfo,
    val description: String
) : ActivityDetailsIntents() {
    override fun reduce(oldState: ActivityDetailState): ActivityDetailState {
        return oldState
    }
}
