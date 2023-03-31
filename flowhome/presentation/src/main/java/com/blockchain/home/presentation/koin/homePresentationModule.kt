package com.blockchain.home.presentation.koin

import com.blockchain.home.presentation.accouncement.AnnouncementsViewModel
import com.blockchain.home.presentation.activity.detail.custodial.CustodialActivityDetailViewModel
import com.blockchain.home.presentation.activity.detail.privatekey.PrivateKeyActivityDetailViewModel
import com.blockchain.home.presentation.activity.list.custodial.CustodialActivityViewModel
import com.blockchain.home.presentation.activity.list.privatekey.PrivateKeyActivityViewModel
import com.blockchain.home.presentation.allassets.AssetsViewModel
import com.blockchain.home.presentation.allassets.EmptyScreenViewModel
import com.blockchain.home.presentation.dashboard.CustodialEmptyCardViewModel
import com.blockchain.home.presentation.earn.EarnViewModel
import com.blockchain.home.presentation.fiat.actions.FiatActionsNavigator
import com.blockchain.home.presentation.fiat.fundsdetail.FiatFundsDetailViewModel
import com.blockchain.home.presentation.onboarding.defi.DeFiOnboardingViewModel
import com.blockchain.home.presentation.onboarding.introduction.IntroScreensViewModel
import com.blockchain.home.presentation.quickactions.QuickActionsViewModel
import com.blockchain.home.presentation.recurringbuy.list.RecurringBuysViewModel
import com.blockchain.home.presentation.referral.ReferralViewModel
import com.blockchain.koin.activeRewardsAccountFeatureFlag
import com.blockchain.koin.dexFeatureFlag
import com.blockchain.koin.iterableAnnouncementsFeatureFlag
import com.blockchain.koin.payloadScopeQualifier
import kotlinx.coroutines.CoroutineScope
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val homePresentationModule = module {
    viewModel {
        IntroScreensViewModel(educationalScreensPrefs = get())
    }

    scope(payloadScopeQualifier) {
        viewModel {
            DeFiOnboardingViewModel(walletStatusPrefs = get())
        }

        viewModel {
            AnnouncementsViewModel(
                walletModeService = get(),
                backupPhraseService = get(),
                announcementsService = get(),
                iterableAnnouncementsFF = get(iterableAnnouncementsFeatureFlag),
            )
        }

        viewModel {
            AssetsViewModel(
                homeAccountsService = get(),
                currencyPrefs = get(),
                exchangeRates = get(),
                filterService = get(),
                assetCatalogue = get(),
                walletModeService = get(),
                coincore = get()
            )
        }

        viewModel { (fiatTicker: String) ->
            FiatFundsDetailViewModel(
                fiatTicker = fiatTicker,
                homeAccountsService = get(),
                fiatActions = get()
            )
        }

        scoped { (scope: CoroutineScope) ->
            FiatActionsNavigator(
                scope = scope,
                fiatActions = get()
            )
        }

        viewModel { (
            homeVm: AssetsViewModel,
            pkwActivityViewModel: PrivateKeyActivityViewModel,
            custodialActivityViewModel: CustodialActivityViewModel
        ) ->
            EmptyScreenViewModel(
                homeAssetsViewModel = homeVm,
                walletModeService = get(),
                pkwActivityViewModel = pkwActivityViewModel,
                custodialActivityViewModel = custodialActivityViewModel
            )
        }

        viewModel {
            PrivateKeyActivityViewModel(
                unifiedActivityService = get(),
                walletModeService = get()
            )
        }

        viewModel {
            RecurringBuysViewModel(
                recurringBuyService = get()
            )
        }

        viewModel {
            CustodialActivityViewModel(
                custodialActivityService = get(),
                walletModeService = get()
            )
        }

        viewModel { (txId: String) ->
            PrivateKeyActivityDetailViewModel(
                activityTxId = txId,
                unifiedActivityService = get()
            )
        }

        viewModel { (txId: String) ->
            CustodialActivityDetailViewModel(
                activityTxId = txId,
                custodialActivityService = get(),
                paymentMethodService = get(),
                cardService = get(),
                bankService = get(),
                coincore = get(),
                defaultLabels = get(),
                recurringBuyService = get()
            )
        }

        viewModel {
            QuickActionsViewModel(
                walletModeService = get(),
                coincore = get(),
                dexFeatureFlag = get(dexFeatureFlag),
                quickActionsService = get(),
                fiatCurrenciesService = get(),
                fiatActions = get()
            )
        }

        viewModel {
            EarnViewModel(
                walletModeService = get(),
                stakingService = get(),
                exchangeRates = get(),
                coincore = get(),
                interestService = get(),
                activeRewardsService = get(),
                activeRewardsFeatureFlag = get(activeRewardsAccountFeatureFlag),
            )
        }

        viewModel {
            CustodialEmptyCardViewModel(
                fiatCurrenciesService = get(),
                userFeaturePermissionService = get(),
                onBoardingStepsService = get(),
                custodialEmptyCardService = get()
            )
        }

        viewModel {
            ReferralViewModel(
                referralService = get()
            )
        }
    }
}
