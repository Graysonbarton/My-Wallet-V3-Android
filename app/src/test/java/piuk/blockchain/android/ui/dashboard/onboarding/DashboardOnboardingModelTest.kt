package piuk.blockchain.android.ui.dashboard.onboarding

import com.blockchain.android.testutils.rxInit
import com.blockchain.domain.fiatcurrencies.FiatCurrenciesService
import com.blockchain.domain.onboarding.CompletableDashboardOnboardingStep
import com.blockchain.domain.onboarding.DashboardOnboardingStep
import com.blockchain.domain.onboarding.DashboardOnboardingStep.BUY
import com.blockchain.domain.onboarding.DashboardOnboardingStep.LINK_PAYMENT_METHOD
import com.blockchain.domain.onboarding.DashboardOnboardingStep.UPGRADE_TO_GOLD
import com.blockchain.domain.onboarding.DashboardOnboardingStepState
import com.blockchain.domain.paymentmethods.model.BankPartner
import com.blockchain.domain.paymentmethods.model.LinkBankAttributes
import com.blockchain.domain.paymentmethods.model.LinkBankTransfer
import com.blockchain.domain.paymentmethods.model.PaymentLimits
import com.blockchain.domain.paymentmethods.model.PaymentMethod
import com.blockchain.domain.paymentmethods.model.PaymentMethodType
import com.blockchain.domain.paymentmethods.model.YodleeAttributes
import com.blockchain.enviroment.EnvironmentConfig
import com.blockchain.testutils.EUR
import com.blockchain.testutils.numberToBigInteger
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.math.BigInteger
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import piuk.blockchain.android.domain.usecases.AvailablePaymentMethodType
import piuk.blockchain.android.domain.usecases.LinkAccess

class DashboardOnboardingModelTest {

    private val interactor: DashboardOnboardingInteractor = mock {
        on { getSteps() }.thenReturn(Single.just(STEPS_INITIAL))
    }

    private lateinit var model: DashboardOnboardingModel

    private val fiatCurrenciesService: FiatCurrenciesService = mock()
    private val environmentConfig: EnvironmentConfig = mock {
        on { isRunningInDebugMode() }.thenReturn(false)
    }

    @get:Rule
    val rx = rxInit {
        ioTrampoline()
        computationTrampoline()
    }

    @Before
    fun setUp() {
        model = DashboardOnboardingModel(
            initialSteps = STEPS_INITIAL,
            interactor = interactor,
            fiatCurrenciesService = fiatCurrenciesService,
            uiScheduler = Schedulers.io(),
            environmentConfig = environmentConfig,
            remoteLogger = mock()
        )
    }

    @Test
    fun `fetching steps success should show steps`() {
        val steps = listOf(
            CompletableDashboardOnboardingStep(UPGRADE_TO_GOLD, DashboardOnboardingStepState.COMPLETE),
            CompletableDashboardOnboardingStep(LINK_PAYMENT_METHOD, DashboardOnboardingStepState.INCOMPLETE),
            CompletableDashboardOnboardingStep(BUY, DashboardOnboardingStepState.INCOMPLETE),
        )
        whenever(interactor.getSteps()).thenReturn(Single.just(steps))

        val state = model.state.test()
        model.process(DashboardOnboardingIntent.FetchSteps)

        state.assertValueAt(0) {
            it.steps == STEPS_INITIAL
        }
        state.assertValueAt(1) {
            it.steps == steps
        }

        verify(interactor).getSteps()
    }

    @Test
    fun `fetching steps failure should show error`() {
        val error = IllegalStateException("error")
        whenever(interactor.getSteps()).thenReturn(Single.error(error))

        val state = model.state.test()
        model.process(DashboardOnboardingIntent.FetchSteps)

        state.assertValueAt(1) {
            it.error == error
        }
    }

    @Test
    fun `clicking upgrade to gold step should navigate to kyc`() {
        val state = model.state.test()
        model.process(DashboardOnboardingIntent.StepClicked(UPGRADE_TO_GOLD))

        state.assertValueAt(1) {
            it.navigationAction == DashboardOnboardingNavigationAction.StartKyc
        }
    }

    @Test
    fun `given upgrade to gold step not complete, clicking link payment method step should navigate to kyc`() {
        whenever(interactor.getSteps()).thenReturn(Single.just(STEPS_INITIAL))

        val state = model.state.test()
        model.process(DashboardOnboardingIntent.StepClicked(LINK_PAYMENT_METHOD))

        state.assertValueAt(1) {
            it.navigationAction == DashboardOnboardingNavigationAction.StartKyc
        }
    }

    @Test
    fun `given upgrade to gold step pending, clicking link payment method step should navigate to kyc`() {
        val steps = listOf(
            CompletableDashboardOnboardingStep(UPGRADE_TO_GOLD, DashboardOnboardingStepState.PENDING),
            CompletableDashboardOnboardingStep(LINK_PAYMENT_METHOD, DashboardOnboardingStepState.INCOMPLETE),
            CompletableDashboardOnboardingStep(BUY, DashboardOnboardingStepState.INCOMPLETE),
        )
        whenever(interactor.getSteps()).thenReturn(Single.just(steps))

        val state = model.state.test()
        model.process(DashboardOnboardingIntent.StepClicked(LINK_PAYMENT_METHOD))

        state.assertValueAt(1) {
            it.navigationAction == DashboardOnboardingNavigationAction.StartKyc
        }
    }

    @Test
    fun `given user clicked link payment method step, fetching eligible payment methods success should navigate to add payment method`() {
        val currency = EUR
        val limits = PaymentLimits(BigInteger.ZERO, BigInteger.ZERO, currency)
        val availablePaymentMethodTypes = listOf(
            AvailablePaymentMethodType(true, LinkAccess.GRANTED, currency, PaymentMethodType.PAYMENT_CARD, limits),
            AvailablePaymentMethodType(true, LinkAccess.GRANTED, currency, PaymentMethodType.BANK_ACCOUNT, limits),
            AvailablePaymentMethodType(true, LinkAccess.BLOCKED, currency, PaymentMethodType.BANK_TRANSFER, limits)
        )

        whenever(interactor.getAvailablePaymentMethodTypes(currency)).thenReturn(
            Single.just(availablePaymentMethodTypes)
        )
        whenever(interactor.getSteps()).thenReturn(Single.just(STEPS_UPGRADE_TO_GOLD_COMPLETE))
        whenever(fiatCurrenciesService.selectedTradingCurrency).thenReturn(currency)
        whenever(interactor.getAvailablePaymentMethodTypes(currency)).thenReturn(
            Single.just(availablePaymentMethodTypes)
        )

        val state = model.state.test()
        model.process(DashboardOnboardingIntent.FetchSteps)
        model.process(DashboardOnboardingIntent.StepClicked(LINK_PAYMENT_METHOD))

        val paymentMethods = listOf(
            PaymentMethod.UndefinedCard(
                PaymentLimits(0.numberToBigInteger(), 0.numberToBigInteger(), currency), true, null
            ),
            PaymentMethod.UndefinedBankAccount(
                currency, PaymentLimits(0.numberToBigInteger(), 0.numberToBigInteger(), currency), true
            )
        )
        state.assertValueAt(2) {
            it.navigationAction == DashboardOnboardingNavigationAction.AddPaymentMethod(paymentMethods)
        }
    }

    @Test
    fun `given user clicked link payment method step, fetching eligible payment methods failure should show error`() {
        val currency = EUR
        val error = IllegalStateException("error")
        whenever(interactor.getSteps()).thenReturn(Single.just(STEPS_UPGRADE_TO_GOLD_COMPLETE))
        whenever(fiatCurrenciesService.selectedTradingCurrency).thenReturn(currency)
        whenever(interactor.getAvailablePaymentMethodTypes(currency)).thenReturn(Single.error(error))

        val state = model.state.test()
        model.process(DashboardOnboardingIntent.FetchSteps)
        model.process(DashboardOnboardingIntent.StepClicked(LINK_PAYMENT_METHOD))

        state.assertValueAt(2) {
            it.error == error
        }
    }

    @Test
    fun `clicking add card payment method should navigate to Add card`() {
        val state = model.state.test()
        model.process(DashboardOnboardingIntent.PaymentMethodClicked(PaymentMethodType.PAYMENT_CARD))

        state.assertValueAt(1) {
            it.navigationAction == DashboardOnboardingNavigationAction.AddCard
        }
    }

    @Test
    fun `clicking wire transfer payment method should navigate to wire transfer account details`() {
        val currency = EUR
        whenever(fiatCurrenciesService.selectedTradingCurrency).thenReturn(currency)

        val state = model.state.test()
        model.process(DashboardOnboardingIntent.PaymentMethodClicked(PaymentMethodType.FUNDS))

        state.assertValueAt(1) {
            it.navigationAction == DashboardOnboardingNavigationAction.WireTransferAccountDetails(currency)
        }
    }

    @Test
    fun `clicking link bank payment method should fetch link bank transfer`() {
        val currency = EUR
        whenever(fiatCurrenciesService.selectedTradingCurrency).thenReturn(currency)
        whenever(interactor.linkBank(currency)).thenReturn(
            Single.just(
                LinkBankTransfer(
                    "123", BankPartner.YODLEE, YodleeAttributes("", "", "")
                )
            )
        )
        val state = model.state.test()
        model.process(DashboardOnboardingIntent.PaymentMethodClicked(PaymentMethodType.BANK_TRANSFER))

        verify(interactor).linkBank(currency)
    }

    @Test
    fun `fetching link bank transfer success should navigate to link bank`() {
        val currency = EUR
        val linkBankTransfer = LinkBankTransfer(
            "id",
            BankPartner.YAPILY,
            object : LinkBankAttributes {}
        )
        whenever(fiatCurrenciesService.selectedTradingCurrency).thenReturn(currency)
        whenever(interactor.linkBank(currency)).thenReturn(Single.just(linkBankTransfer))

        val state = model.state.test()
        model.process(DashboardOnboardingIntent.PaymentMethodClicked(PaymentMethodType.BANK_TRANSFER))

        state.assertValueAt(1) {
            it.navigationAction == DashboardOnboardingNavigationAction.LinkBank(linkBankTransfer)
        }
    }

    @Test
    fun `fetching link bank transfer failure should show error`() {
        val currency = EUR
        val error = IllegalStateException("error")
        whenever(fiatCurrenciesService.selectedTradingCurrency).thenReturn(currency)
        whenever(interactor.linkBank(currency)).thenReturn(Single.error(error))

        val state = model.state.test()
        model.process(DashboardOnboardingIntent.PaymentMethodClicked(PaymentMethodType.BANK_TRANSFER))

        state.assertValueAt(1) {
            it.error == error
        }
    }

    @Test
    fun `given gold user clicking buy step should navigate to buy`() {
        whenever(interactor.getSteps()).thenReturn(Single.just(STEPS_UPGRADE_TO_GOLD_COMPLETE))

        val state = model.state.test()
        model.process(DashboardOnboardingIntent.FetchSteps)
        model.process(DashboardOnboardingIntent.StepClicked(BUY))

        state.assertValueAt(2) {
            it.navigationAction == DashboardOnboardingNavigationAction.OpenBuy
        }
    }

    companion object {
        private val STEPS_INITIAL = DashboardOnboardingStep.values().map {
            CompletableDashboardOnboardingStep(it, DashboardOnboardingStepState.INCOMPLETE)
        }

        private val STEPS_UPGRADE_TO_GOLD_COMPLETE = DashboardOnboardingStep.values().map {
            val isComplete = when (it) {
                UPGRADE_TO_GOLD -> DashboardOnboardingStepState.COMPLETE
                LINK_PAYMENT_METHOD -> DashboardOnboardingStepState.INCOMPLETE
                BUY -> DashboardOnboardingStepState.INCOMPLETE
            }
            CompletableDashboardOnboardingStep(it, isComplete)
        }
    }
}
