package piuk.blockchain.android.ui.activity.detail

import com.blockchain.coincore.Coincore
import com.blockchain.coincore.NonCustodialActivitySummaryItem
import com.blockchain.coincore.NullCryptoAccount
import com.blockchain.core.chains.bitcoincash.BchDataManager
import com.blockchain.core.payload.PayloadDataManager
import com.blockchain.sunriver.XlmDataManager
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.Money
import info.blockchain.wallet.multiaddress.TransactionSummary
import info.blockchain.wallet.util.FormatsUtil
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.Singles
import piuk.blockchain.android.R
import piuk.blockchain.android.util.StringUtils

class TransactionInOutMapper(
    private val transactionHelper: TransactionHelper,
    private val payloadDataManager: PayloadDataManager,
    private val stringUtils: StringUtils,
    private val bchDataManager: BchDataManager,
    private val xlmDataManager: XlmDataManager,
    private val coincore: Coincore
) {

    fun transformInputAndOutputs(
        item: NonCustodialActivitySummaryItem
    ): Single<TransactionInOutDetails> =
        when (item.currency) {
            CryptoCurrency.BTC -> handleBtcToAndFrom(item)
            CryptoCurrency.BCH -> handleBchToAndFrom(item)
            CryptoCurrency.XLM -> handleXlmToAndFrom(item)
            else -> handleDynamicAssetsToAndFrom(item)
        }

    private fun handleXlmToAndFrom(activitySummaryItem: NonCustodialActivitySummaryItem) =
        xlmDataManager.defaultAccount()
            .map { account ->
                var fromAddress = activitySummaryItem.inputsMap.keys.first()
                var toAddress = activitySummaryItem.outputsMap.keys.first()
                if (fromAddress == account.accountId) {
                    fromAddress = account.label
                }
                if (toAddress == account.accountId) {
                    toAddress = account.label
                }

                TransactionInOutDetails(
                    inputs = listOf(
                        TransactionDetailModel(
                            fromAddress
                        )
                    ),
                    outputs = listOf(
                        TransactionDetailModel(
                            toAddress
                        )
                    )
                )
            }

    private fun handleDynamicAssetsToAndFrom(
        activitySummaryItem: NonCustodialActivitySummaryItem
    ): Single<TransactionInOutDetails> {

        val fromAddress = activitySummaryItem.inputsMap.keys.first()
        val toAddress = activitySummaryItem.outputsMap.keys.first()

        return Singles.zip(
            coincore.findAccountByAddress(activitySummaryItem.currency as AssetInfo, fromAddress)
                .defaultIfEmpty(NullCryptoAccount(fromAddress)),
            coincore.findAccountByAddress(activitySummaryItem.currency as AssetInfo, toAddress)
                .defaultIfEmpty(NullCryptoAccount(toAddress))
        ) { fromAccount, toAccount ->
            TransactionInOutDetails(
                inputs = listOf(
                    TransactionDetailModel(
                        fromAccount.label.takeIf { it.isNotEmpty() } ?: fromAddress
                    )
                ),
                outputs = listOf(
                    TransactionDetailModel(
                        toAccount.label.takeIf { it.isNotEmpty() } ?: toAddress
                    )
                )
            )
        }
    }

    private fun handleBtcToAndFrom(activitySummaryItem: NonCustodialActivitySummaryItem) =
        Single.fromCallable {
            val (inputs, outputs) = transactionHelper.filterNonChangeBtcAddresses(
                activitySummaryItem
            )
            setToAndFrom(CryptoCurrency.BTC, inputs, outputs)
        }

    private fun handleBchToAndFrom(activitySummaryItem: NonCustodialActivitySummaryItem) =
        Single.fromCallable {
            val (inputs, outputs) = transactionHelper.filterNonChangeBchAddresses(activitySummaryItem)
            setToAndFrom(CryptoCurrency.BCH, inputs, outputs)
        }

    private fun setToAndFrom(
        asset: AssetInfo,
        inputs: Map<String, Money>,
        outputs: Map<String, Money>
    ) = TransactionInOutDetails(
        inputs = getFromList(asset, inputs),
        outputs = getToList(asset, outputs)
    )

    private fun getFromList(
        currency: AssetInfo,
        inputMap: Map<String, Money>
    ): List<TransactionDetailModel> {
        val inputs = handleTransactionMap(inputMap, currency)
        // No inputs = coinbase transaction
        if (inputs.isEmpty()) {
            val coinbase =
                TransactionDetailModel(
                    address = stringUtils.getString(R.string.transaction_detail_coinbase),
                    displayUnits = currency.displayTicker
                )
            inputs.add(coinbase)
        }
        return inputs.toList()
    }

    private fun getToList(
        currency: AssetInfo,
        outputMap: Map<String, Money>
    ): List<TransactionDetailModel> = handleTransactionMap(outputMap, currency)

    private fun handleTransactionMap(
        inputMap: Map<String, Money>,
        currency: AssetInfo
    ): MutableList<TransactionDetailModel> {
        val inputs = mutableListOf<TransactionDetailModel>()
        for ((address, value) in inputMap) {
            val label = if (currency == CryptoCurrency.BTC) {
                payloadDataManager.addressToLabel(address)
            } else {
                bchDataManager.getLabelFromBchAddress(address)
                    ?: FormatsUtil.toShortCashAddress(address)
            }

            val transactionDetailModel = buildTransactionDetailModel(label, value, currency)
            inputs.add(transactionDetailModel)
        }
        return inputs
    }

    private fun buildTransactionDetailModel(
        label: String,
        value: Money,
        cryptoCurrency: AssetInfo
    ): TransactionDetailModel =
        TransactionDetailModel(
            label,
            value.toStringWithoutSymbol(),
            cryptoCurrency.displayTicker
        ).apply {
            if (address == TransactionSummary.ADDRESS_DECODE_ERROR) {
                address = stringUtils.getString(R.string.tx_decode_error)
                addressDecodeError = true
            }
        }
}
