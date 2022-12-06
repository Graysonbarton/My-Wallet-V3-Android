package piuk.blockchain.android.ui.activity.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.coincore.FiatActivitySummaryItem
import com.blockchain.componentlib.viewextensions.gone
import com.blockchain.componentlib.viewextensions.visible
import com.blockchain.nabu.datamanagers.TransactionState
import com.blockchain.nabu.datamanagers.TransactionType
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.presentation.getResolvedColor
import com.blockchain.utils.toFormattedDate
import info.blockchain.balance.FiatCurrency
import java.util.Date
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.LayoutFiatActivityItemBinding
import piuk.blockchain.android.ui.activity.ActivityType
import piuk.blockchain.android.ui.adapters.AdapterDelegate
import piuk.blockchain.android.util.context
import piuk.blockchain.android.util.setTransactionHasFailed

class CustodialFiatActivityItemDelegate<in T>(
    private val prefs: CurrencyPrefs,
    private val onItemClicked: (FiatCurrency, String, ActivityType) -> Unit
) : AdapterDelegate<T> {

    override fun isForViewType(items: List<T>, position: Int): Boolean =
        items[position] is FiatActivitySummaryItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder =
        FiatActivityItemViewHolder(
            LayoutFiatActivityItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(items: List<T>, position: Int, holder: RecyclerView.ViewHolder) {
        (holder as FiatActivityItemViewHolder).bind(
            items[position] as FiatActivitySummaryItem,
            prefs.selectedFiatCurrency,
            onItemClicked
        )
    }
}

private class FiatActivityItemViewHolder(
    private val binding: LayoutFiatActivityItemBinding
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(
        tx: FiatActivitySummaryItem,
        selectedFiatCurrency: FiatCurrency,
        onAccountClicked: (FiatCurrency, String, ActivityType) -> Unit
    ) {
        with(binding) {
            when {
                tx.state.isPending() -> renderPending()
                tx.state.hasFailed() -> renderFailed()
                tx.state.hasCompleted() -> renderComplete(tx)
                else -> throw IllegalArgumentException("TransactionState not valid")
            }

            txType.setTxLabel(tx.currency, tx.type)

            statusDate.text = Date(tx.timeStampMs).toFormattedDate()

            assetBalanceFiat.text = tx.value.toStringWithSymbol()

            if (tx.currency != selectedFiatCurrency) {
                assetBalanceFiatExchange.visible()
                assetBalanceFiatExchange.text = tx.fiatValue(selectedFiatCurrency).toStringWithSymbol()
            } else {
                assetBalanceFiatExchange.gone()
            }

            txRoot.setOnClickListener { onAccountClicked(tx.currency, tx.txId, ActivityType.UNKNOWN) }
        }
    }

    private fun LayoutFiatActivityItemBinding.renderComplete(tx: FiatActivitySummaryItem) {
        icon.apply {
            setImageResource(
                if (tx.type == TransactionType.DEPOSIT)
                    R.drawable.ic_tx_buy else
                    R.drawable.ic_tx_sell
            )
            setBackgroundResource(R.drawable.bkgd_tx_circle)
            background.setTint(context.getResolvedColor(R.color.green_500_fade_15))
            setColorFilter(context.getResolvedColor(R.color.green_500))
        }

        txType.setTextColor(context.getResolvedColor(R.color.black))
        statusDate.setTextColor(context.getResolvedColor(R.color.grey_600))
        assetBalanceFiat.setTextColor(context.getResolvedColor(R.color.black))
    }

    private fun LayoutFiatActivityItemBinding.renderPending() {
        txType.setTextColor(context.getResolvedColor(R.color.grey_400))
        statusDate.setTextColor(context.getResolvedColor(R.color.grey_400))
        assetBalanceFiat.setTextColor(context.getResolvedColor(R.color.grey_400))
        icon.apply {
            setImageResource(R.drawable.ic_tx_confirming)
            background = null
            setColorFilter(Color.TRANSPARENT)
        }
    }

    private fun LayoutFiatActivityItemBinding.renderFailed() {
        txType.setTextColor(ContextCompat.getColor(context, R.color.black))
        statusDate.setTextColor(ContextCompat.getColor(context, R.color.grey_600))
        assetBalanceFiat.setTextColor(ContextCompat.getColor(context, R.color.grey_600))
        icon.setTransactionHasFailed()
    }

    private fun TransactionState.isPending() =
        this == TransactionState.PENDING

    private fun TransactionState.hasFailed() =
        this == TransactionState.FAILED

    private fun TransactionState.hasCompleted() =
        this == TransactionState.COMPLETED
}

private fun AppCompatTextView.setTxLabel(currency: FiatCurrency, type: TransactionType) {
    text = when (type) {
        TransactionType.DEPOSIT -> context.getString(R.string.tx_title_deposited, currency.displayTicker)
        else -> context.getString(R.string.tx_title_withdrawn, currency.displayTicker)
    }
}
