package com.dex.presentation.koin

import com.blockchain.koin.payloadScopeQualifier
import com.dex.domain.AllowanceTransactionProcessor
import com.dex.domain.DexTransactionProcessor
import com.dex.presentation.DexSelectDestinationAccountViewModel
import com.dex.presentation.DexSourceAccountViewModel
import com.dex.presentation.SettingsViewModel
import com.dex.presentation.TokenAllowanceViewModel
import com.dex.presentation.confirmation.DexConfirmationViewModel
import com.dex.presentation.enteramount.DexEnterAmountViewModel
import com.dex.presentation.inprogress.DexInProgressTxViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val dexPresentation = module {
    scope(payloadScopeQualifier) {
        viewModel {
            DexEnterAmountViewModel(
                currencyPrefs = get(),
                txProcessor = get(),
                dexAccountsService = get(),
                exchangeRatesDataManager = get(),
                allowanceProcessor = get(),
                dexSlippageService = get()
            )
        }

        viewModel {
            DexConfirmationViewModel(
                transactionProcessor = get(),
                exchangeRatesDataManager = get(),
                currencyPrefs = get()
            )
        }

        viewModel {
            DexInProgressTxViewModel(
                txProcessor = get()
            )
        }

        viewModel {
            DexSourceAccountViewModel(
                dexService = get(),
                transactionProcessor = get()
            )
        }

        viewModel {
            DexSelectDestinationAccountViewModel(
                dexService = get(),
                transactionProcessor = get()
            )
        }
        viewModel {
            SettingsViewModel(
                slippageService = get(),
                txProcessor = get(),
            )
        }

        viewModel {
            TokenAllowanceViewModel(
                assetCatalogue = get(),
            )
        }

        scoped {
            DexTransactionProcessor(
                dexQuotesService = get(),
                allowanceService = get(),
                evmNetworkSigner = get(),
                dexTransactionService = get(),
                balanceService = get()
            )
        }

        factory {
            AllowanceTransactionProcessor(
                allowanceService = get(),
                evmNetworkSigner = get()
            )
        }
    }
}
