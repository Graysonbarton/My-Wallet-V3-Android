package com.blockchain.presentation.onboarding

import app.cash.turbine.test
import com.blockchain.presentation.onboarding.viewmodel.DeFiOnboardingViewModel
import com.blockchain.testutils.CoroutineTestRule
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeFiOnboardingViewModelTest {

    @get:Rule
    var coroutineTestRule = CoroutineTestRule()

    private lateinit var viewModel: DeFiOnboardingViewModel

    @Before
    fun setUp() {
        viewModel = DeFiOnboardingViewModel()
    }

    @Test
    fun `WHEN EnableDeFiWallet is called, THEN requiresPinVerification should be true`() =
        runTest {
            viewModel.viewState.test {
                viewModel.onIntent(DeFiOnboardingIntent.EnableDeFiWallet)

                val state = expectMostRecentItem()
                assertEquals(true, state.shouldLaunchPhraseBackup)
            }
        }

    @Test
    fun `WHEN PhraseBackupRequested is called, THEN requiresPinVerification should be false`() =
        runTest {
            viewModel.viewState.test {
                viewModel.onIntent(DeFiOnboardingIntent.PhraseBackupRequested)

                val state = expectMostRecentItem()
                assertEquals(false, state.shouldLaunchPhraseBackup)
            }
        }

    @Test
    fun `WHEN EndFlow with isSuccessful true is called, THEN flowState should be Ended with isSuccessful true`() =
        runTest {
            viewModel.viewState.test {
                viewModel.onIntent(DeFiOnboardingIntent.EndFlow(isSuccessful = true))

                val state = expectMostRecentItem()
                assertEquals(FlowState.Ended(true), state.flowState)
            }
        }

    @Test
    fun `WHEN EndFlow with isSuccessful false is called, THEN flowState should be Ended with isSuccessful false`() =
        runTest {
            viewModel.viewState.test {
                viewModel.onIntent(DeFiOnboardingIntent.EndFlow(isSuccessful = false))

                val state = expectMostRecentItem()
                assertEquals(FlowState.Ended(false), state.flowState)
            }
        }
}
