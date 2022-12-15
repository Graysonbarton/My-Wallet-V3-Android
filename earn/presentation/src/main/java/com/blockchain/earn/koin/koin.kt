package com.blockchain.earn.koin

import com.blockchain.earn.dashboard.viewmodel.EarnDashboardViewModel
import com.blockchain.earn.staking.viewmodel.StakingSummaryViewModel
import com.blockchain.koin.payloadScopeQualifier
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val earnPresentationModule = module {
    scope(payloadScopeQualifier) {
        viewModel {
            StakingSummaryViewModel(
                coincore = get(),
                stakingService = get(),
                exchangeRatesDataManager = get()
            )
        }

        viewModel {
            EarnDashboardViewModel(
                coincore = get(),
                stakingService = get(),
                exchangeRatesDataManager = get(),
                interestService = get(),
                userIdentity = get(),
                assetCatalogue = get(),
                custodialWalletManager = get()
            )
        }
    }
}
