package com.blockchain.presentation.viewmodel

import androidx.lifecycle.viewModelScope
import com.blockchain.commonarch.presentation.mvi_v2.ModelConfigArgs
import com.blockchain.commonarch.presentation.mvi_v2.MviViewModel
import com.blockchain.extensions.exhaustive
import com.blockchain.presentation.BackUpStatus
import com.blockchain.presentation.CopyState
import com.blockchain.presentation.DefaultPhraseViewState
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DefaultPhraseViewModel : MviViewModel<DefaultPhraseIntent,
    DefaultPhraseViewState,
    DefaultPhraseModelState,
    DefaultPhraseNavigationEvent,
    ModelConfigArgs.NoArgs>(
    initialState = DefaultPhraseModelState()
) {
    override fun viewCreated(args: ModelConfigArgs.NoArgs) {
        onIntent(DefaultPhraseIntent.LoadDefaultPhrase)
    }

    override fun reduce(state: DefaultPhraseModelState): DefaultPhraseViewState {
        return with(state) {
            DefaultPhraseViewState(
                showProgress = isLoading,
                mnemonic = mnemonic,
                mnemonicString = mnemonic.joinToString(separator = " "),
                backUpStatus = if (hasBackup) BackUpStatus.BACKED_UP else BackUpStatus.NO_BACKUP,
                copyState = copyState
            )
        }
    }

    override suspend fun handleIntent(modelState: DefaultPhraseModelState, intent: DefaultPhraseIntent) {
        when (intent) {
            DefaultPhraseIntent.LoadDefaultPhrase -> {
                updateState {
                    modelState.copy(
                        mnemonic = mnemonic()
                    )
                }
            }

            DefaultPhraseIntent.MnemonicCopied -> {
                resetCopyState()
                updateState { it.copy(copyState = CopyState.Copied) }
            }

            DefaultPhraseIntent.ResetCopy -> {
                updateState { it.copy(copyState = CopyState.Idle) }
            }
        }.exhaustive
    }

    private fun mnemonic(): List<String> {
        val locales = Locale.getISOCountries().toList()
        return locales.map {
            Locale("", it).isO3Country
        }.shuffled().subList(0, 12)
    }

    private fun resetCopyState() {
        viewModelScope.launch {
            delay(TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES))
            onIntent(DefaultPhraseIntent.ResetCopy)
            // todo reset clipboard
        }
    }
}
