package com.blockchain.chrome

import androidx.lifecycle.viewModelScope
import com.blockchain.chrome.composable.bottomNavigationItems
import com.blockchain.commonarch.presentation.mvi_v2.ModelConfigArgs
import com.blockchain.commonarch.presentation.mvi_v2.MviViewModel
import com.blockchain.commonarch.presentation.mvi_v2.NavigationEvent
import com.blockchain.data.map
import com.blockchain.walletmode.WalletMode
import com.blockchain.walletmode.WalletModeBalanceService
import com.blockchain.walletmode.WalletModeService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MultiAppViewModel(
    private val walletModeService: WalletModeService,
    private val walletModeBalanceService: WalletModeBalanceService
) : MviViewModel<
    MultiAppIntents,
    MultiAppViewState,
    MultiAppModelState,
    NavigationEvent,
    ModelConfigArgs.NoArgs>(
    MultiAppModelState(
        // todo handle defi mode only
        walletModes = walletModeService.availableModes(),
        selectedWalletMode = WalletMode.CUSTODIAL_ONLY
    )
) {

    override fun viewCreated(args: ModelConfigArgs.NoArgs) {
        viewModelScope.launch {
            // collect wallet mode changes rather than manually change it when user switches modes
            // as there are cases where it will change automatically
            walletModeService.walletMode.collectLatest { walletMode ->
                updateState {
                    it.copy(selectedWalletMode = walletMode)
                }
            }
        }

        loadTotalBalance()
    }

    override fun reduce(state: MultiAppModelState): MultiAppViewState = state.run {
        MultiAppViewState(
            modeSwitcherOptions = if (state.walletModes.size == 1) {
                ChromeModeOptions.SingleSelection(state.walletModes.first())
            } else {
                ChromeModeOptions.MultiSelection(state.walletModes)
            },
            selectedMode = state.selectedWalletMode,
            backgroundColors = state.selectedWalletMode.backgroundColors(),
            totalBalance = state.totalBalance.map { balance -> balance.toStringWithSymbol() },
            shouldRevealBalance = state.balanceRevealed.not(),
            bottomNavigationItems = state.selectedWalletMode.bottomNavigationItems()
        )
    }

    override suspend fun handleIntent(modelState: MultiAppModelState, intent: MultiAppIntents) {
        when (intent) {
            is MultiAppIntents.WalletModeChanged -> {
                walletModeService.updateEnabledWalletMode(intent.walletMode)
            }

            is MultiAppIntents.BalanceRevealed -> {
                updateState {
                    it.copy(balanceRevealed = true)
                }
            }
        }
    }

    private fun loadTotalBalance() {
        viewModelScope.launch {
            walletModeBalanceService.totalBalance()
                .distinctUntilChanged()
                .debounce(1000)
                .collectLatest { totalBalanceDataResource ->
                    updateState {
                        it.copy(totalBalance = totalBalanceDataResource)
                    }
                }
        }
    }
}
