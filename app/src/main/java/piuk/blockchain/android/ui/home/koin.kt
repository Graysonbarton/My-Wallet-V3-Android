package piuk.blockchain.android.ui.home

import com.blockchain.analytics.TraitsService
import com.blockchain.koin.blockchainMembershipsFeatureFlag
import com.blockchain.koin.earnTabFeatureFlag
import com.blockchain.koin.payloadScopeQualifier
import com.blockchain.koin.stakingAccountFeatureFlag
import com.blockchain.koin.superAppModeService
import com.blockchain.walletmode.WalletModeBalanceService
import com.blockchain.walletmode.WalletModeService
import com.blockchain.walletmode.WalletModeStore
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import piuk.blockchain.android.ui.dashboard.walletmode.WalletModeSelectionViewModel
import piuk.blockchain.android.ui.home.models.ActionsSheetInteractor
import piuk.blockchain.android.ui.home.models.ActionsSheetModel
import piuk.blockchain.android.ui.home.models.ActionsSheetState
import piuk.blockchain.android.ui.home.models.MainInteractor
import piuk.blockchain.android.ui.home.models.MainModel
import piuk.blockchain.android.ui.home.models.MainState
import piuk.blockchain.android.walletmode.SuperAppWalletModeRepository
import piuk.blockchain.android.walletmode.WalletModeBalanceRepository
import piuk.blockchain.android.walletmode.WalletModePrefStore
import piuk.blockchain.android.walletmode.WalletModeRepository
import piuk.blockchain.android.walletmode.WalletModeTraitsRepository

val mainModule = module {

    scope(payloadScopeQualifier) {
        factory {
            MainModel(
                initialState = MainState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                interactor = get(),
                walletConnectServiceAPI = get(),
                environmentConfig = get(),
                remoteLogger = get()
            )
        }

        factory {
            MainInteractor(
                deepLinkProcessor = get(),
                deeplinkRedirector = get(),
                deepLinkPersistence = get(),
                exchangeLinking = get(),
                assetCatalogue = get(),
                bankLinkingPrefs = get(),
                bankService = get(),
                simpleBuySync = get(),
                userIdentity = get(),
                upsellManager = get(),
                credentialsWiper = get(),
                qrScanResultProcessor = get(),
                secureChannelService = get(),
                cancelOrderUseCase = get(),
                referralPrefs = get(),
                referralRepository = get(),
                ethDataManager = get(),
                stakingAccountFlag = get(stakingAccountFeatureFlag),
                membershipFlag = get(blockchainMembershipsFeatureFlag),
                coincore = get(),
                walletModeService = get(),
                earnOnNavBarFlag = get(earnTabFeatureFlag)
            )
        }

        factory {
            ActionsSheetModel(
                initialState = ActionsSheetState(),
                mainScheduler = AndroidSchedulers.mainThread(),
                interactor = get(),
                environmentConfig = get(),
                remoteLogger = get()
            )
        }

        factory {
            ActionsSheetInteractor(
                userIdentity = get()
            )
        }
        viewModel {
            WalletModeSelectionViewModel(
                walletModeService = get(),
                payloadManager = get(),
                walletModeBalanceService = get(),
                walletStatusPrefs = get(),
                walletModePrefs = get()
            )
        }

        scoped<WalletModeBalanceService> {
            WalletModeBalanceRepository(
                coincore = get(),
                balanceStore = get(),
                currencyPrefs = get()
            )
        }

        scoped(superAppModeService) {
            SuperAppWalletModeRepository(
                walletModeStore = get(),
                defaultWalletModeStrategy = get()
            )
        }.bind(WalletModeService::class)

        scoped {
            WalletModeRepository(
                walletModeStore = get(),
                defaultWalletModeStrategy = get()
            )
        }.bind(WalletModeService::class)
    }

    single {
        WalletModePrefStore(
            walletModePrefs = get()
        )
    }.bind(WalletModeStore::class)

    factory {
        WalletModeTraitsRepository()
    }.bind(TraitsService::class)
}
