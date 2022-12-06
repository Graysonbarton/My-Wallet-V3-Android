package piuk.blockchain.android.simplebuy

import com.blockchain.analytics.Analytics
import com.blockchain.api.paymentmethods.models.SimpleBuyConfirmationAttributes
import com.blockchain.banking.BankPartnerCallbackProvider
import com.blockchain.banking.BankTransferAction
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.Coincore
import com.blockchain.core.buy.domain.SimpleBuyService
import com.blockchain.core.custodial.BrokerageDataManager
import com.blockchain.core.custodial.models.BrokerageQuote
import com.blockchain.core.kyc.domain.KycService
import com.blockchain.core.kyc.domain.model.KycTier
import com.blockchain.core.kyc.domain.model.KycTiers
import com.blockchain.core.limits.LimitsDataManager
import com.blockchain.core.limits.TxLimit
import com.blockchain.core.limits.TxLimits
import com.blockchain.core.payments.PaymentsRepository
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.coreandroid.remoteconfig.RemoteConfigRepository
import com.blockchain.domain.eligibility.EligibilityService
import com.blockchain.domain.eligibility.model.GetRegionScope
import com.blockchain.domain.eligibility.model.Region
import com.blockchain.domain.paymentmethods.BankService
import com.blockchain.domain.paymentmethods.CardService
import com.blockchain.domain.paymentmethods.PaymentMethodService
import com.blockchain.domain.paymentmethods.model.BankPartner
import com.blockchain.domain.paymentmethods.model.BankProviderAccountAttributes
import com.blockchain.domain.paymentmethods.model.BillingAddress
import com.blockchain.domain.paymentmethods.model.CardRejectionState
import com.blockchain.domain.paymentmethods.model.CardStatus
import com.blockchain.domain.paymentmethods.model.CardToBeActivated
import com.blockchain.domain.paymentmethods.model.EligiblePaymentMethodType
import com.blockchain.domain.paymentmethods.model.LegacyLimits
import com.blockchain.domain.paymentmethods.model.LinkedBank
import com.blockchain.domain.paymentmethods.model.LinkedPaymentMethod
import com.blockchain.domain.paymentmethods.model.PaymentMethod
import com.blockchain.domain.paymentmethods.model.PaymentMethodType
import com.blockchain.featureflag.FeatureFlag
import com.blockchain.nabu.Feature
import com.blockchain.nabu.UserIdentity
import com.blockchain.nabu.datamanagers.BuySellOrder
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.OrderInput
import com.blockchain.nabu.datamanagers.OrderOutput
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.PaymentCardAcquirer
import com.blockchain.nabu.datamanagers.Product
import com.blockchain.nabu.datamanagers.RecurringBuyOrder
import com.blockchain.nabu.datamanagers.repositories.WithdrawLocksRepository
import com.blockchain.nabu.models.data.RecurringBuyFrequency
import com.blockchain.nabu.models.responses.simplebuy.CustodialWalletOrder
import com.blockchain.nabu.models.responses.simplebuy.RecurringBuyRequestBody
import com.blockchain.network.PollResult
import com.blockchain.network.PollService
import com.blockchain.outcome.doOnFailure
import com.blockchain.outcome.getOrDefault
import com.blockchain.payments.core.CardAcquirer
import com.blockchain.payments.core.CardBillingAddress
import com.blockchain.payments.core.CardDetails
import com.blockchain.payments.core.CardProcessor
import com.blockchain.payments.core.PaymentToken
import com.blockchain.payments.googlepay.manager.request.BillingAddressParameters
import com.blockchain.payments.googlepay.manager.request.defaultAllowedAuthMethods
import com.blockchain.payments.googlepay.manager.request.defaultAllowedCardNetworks
import com.blockchain.preferences.BankLinkingPrefs
import com.blockchain.preferences.OnboardingPrefs
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.presentation.complexcomponents.QuickFillButtonData
import com.blockchain.presentation.complexcomponents.QuickFillDisplayAndAmount
import com.blockchain.serializers.StringMapSerializer
import com.blockchain.store.asSingle
import com.blockchain.utils.rxSingleOutcome
import info.blockchain.balance.AssetCategory
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.FiatCurrency
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.Singles
import io.reactivex.rxjava3.kotlin.zipWith
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlinx.coroutines.rx3.asCoroutineDispatcher
import kotlinx.coroutines.rx3.rxSingle
import kotlinx.serialization.json.Json
import piuk.blockchain.android.cards.CardData
import piuk.blockchain.android.cards.CardIntent
import piuk.blockchain.android.data.QuotePrice
import piuk.blockchain.android.domain.repositories.TradeDataService
import piuk.blockchain.android.domain.usecases.AvailablePaymentMethodType
import piuk.blockchain.android.domain.usecases.CancelOrderUseCase
import piuk.blockchain.android.domain.usecases.GetAvailablePaymentMethodsTypesUseCase
import piuk.blockchain.android.rating.domain.model.APP_RATING_MINIMUM_BUY_ORDERS
import piuk.blockchain.android.sdd.SDDAnalytics
import piuk.blockchain.android.ui.linkbank.BankAuthDeepLinkState
import piuk.blockchain.android.ui.linkbank.BankAuthFlowState
import piuk.blockchain.android.ui.linkbank.BankAuthSource
import piuk.blockchain.android.ui.linkbank.BankLinkingInfo
import piuk.blockchain.android.ui.linkbank.fromPreferencesValue
import piuk.blockchain.android.ui.linkbank.toPreferencesValue
import piuk.blockchain.android.ui.transactionflow.engine.domain.QuickFillRoundingService
import piuk.blockchain.android.ui.transactionflow.engine.domain.model.QuickFillRoundingData
import timber.log.Timber

class SimpleBuyInteractor(
    private val kycService: KycService,
    private val custodialWalletManager: CustodialWalletManager,
    private val tradeDataService: TradeDataService,
    private val limitsDataManager: LimitsDataManager,
    private val withdrawLocksRepository: WithdrawLocksRepository,
    private val analytics: Analytics,
    private val bankPartnerCallbackProvider: BankPartnerCallbackProvider,
    private val simpleBuyService: SimpleBuyService,
    private val exchangeRatesDataManager: ExchangeRatesDataManager,
    private val coincore: Coincore,
    private val userIdentity: UserIdentity,
    private val bankLinkingPrefs: BankLinkingPrefs,
    private val cardProcessors: Map<CardAcquirer, CardProcessor>,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val getAvailablePaymentMethodsTypesUseCase: GetAvailablePaymentMethodsTypesUseCase,
    private val bankService: BankService,
    private val cardService: CardService,
    private val paymentMethodService: PaymentMethodService,
    private val paymentsRepository: PaymentsRepository,
    private val brokerageDataManager: BrokerageDataManager,
    private val simpleBuyPrefs: SimpleBuyPrefs,
    private val onboardingPrefs: OnboardingPrefs,
    private val eligibilityService: EligibilityService,
    private val cardPaymentAsyncFF: FeatureFlag,
    private val buyQuoteRefreshFF: FeatureFlag,
    private val plaidFF: FeatureFlag,
    private val rbFrequencySuggestionFF: FeatureFlag,
    private val rbExperimentFF: FeatureFlag,
    private val feynmanEnterAmountFF: FeatureFlag,
    private val feynmanCheckoutFF: FeatureFlag,
    private val improvedPaymentUxFF: FeatureFlag,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val quickFillRoundingService: QuickFillRoundingService
) {

    // Hack until we have a proper limits api.
    // ignore limits when user is in tier BRONZE. When user is in tier BRONZE available limit for transfer is 0, so
    // the user will never be able to continue the flow. That's why we don't restrict him. PaymentMethod limit will
    // restrict him only.

    fun fetchBuyLimits(
        fiat: FiatCurrency,
        asset: AssetInfo,
        paymentMethodType: PaymentMethodType
    ): Single<TxLimits> =
        Singles.zip(
            fetchLimits(sourceCurrency = fiat, targetCurrency = asset, paymentMethodType = paymentMethodType),
            kycService.getHighestApprovedTierLevelLegacy()
        ).flatMap { (limits, highestTier) ->
            when (highestTier) {
                KycTier.BRONZE -> Single.just(limits.copy(max = TxLimit.Unlimited))
                KycTier.SILVER -> userIdentity.isVerifiedFor(Feature.SimplifiedDueDiligence).map { isSdd ->
                    if (isSdd) limits
                    else limits.copy(max = TxLimit.Unlimited)
                }
                KycTier.GOLD -> Single.just(limits)
            }
        }

    private fun fetchLimits(
        targetCurrency: AssetInfo,
        sourceCurrency: FiatCurrency,
        paymentMethodType: PaymentMethodType
    ): Single<TxLimits> {
        return limitsDataManager.getLimits(
            outputCurrency = sourceCurrency,
            sourceCurrency = sourceCurrency,
            targetCurrency = targetCurrency,
            targetAccountType = AssetCategory.CUSTODIAL,
            sourceAccountType = if (paymentMethodType == PaymentMethodType.FUNDS) {
                AssetCategory.CUSTODIAL
            } else {
                AssetCategory.NON_CUSTODIAL
            },
            legacyLimits = custodialWalletManager.getProductTransferLimits(
                currency = sourceCurrency,
                product = Product.BUY
            ).map { it as LegacyLimits }
        )
    }

    val stopPollingQuotePrices = PublishSubject.create<Unit>()

    fun getQuotePrice(
        currencyPair: CurrencyPair,
        amount: Money,
        paymentMethod: PaymentMethodType,
    ): Observable<QuotePrice> {
        return tradeDataService.getQuotePrice(
            currencyPair = currencyPair.rawValue,
            amount = amount.toBigInteger().toString(),
            paymentMethod = paymentMethod.name,
            orderProfileName = SIMPLEBUY_PROFILE_NAME
        ).flatMap { quotePrice ->
            Observable.interval(
                INTERVAL_QUOTE_PRICE,
                TimeUnit.MILLISECONDS
            ).flatMap {
                tradeDataService.getQuotePrice(
                    currencyPair = currencyPair.rawValue,
                    amount = amount.toBigInteger().toString(),
                    paymentMethod = paymentMethod.name,
                    orderProfileName = SIMPLEBUY_PROFILE_NAME
                )
            }.startWithItem(
                quotePrice
            )
        }.takeUntil(stopPollingQuotePrices)
    }

    fun getBrokerageQuote(
        cryptoAsset: AssetInfo,
        amount: Money,
        paymentMethodId: String? = null,
        paymentMethod: PaymentMethodType,
    ): Observable<BrokerageQuote> =
        brokerageDataManager.quoteForTransaction(
            pair = CurrencyPair(amount.currency, cryptoAsset),
            amount = amount,
            paymentMethodType = getPaymentMethodType(paymentMethod),
            paymentMethodId = getPaymentMethodId(paymentMethodId, paymentMethod),
            product = Product.BUY
        ).toObservable()

    val stopPollingBrokerageQuotes = PublishSubject.create<Unit>()

    fun startPollingBrokerageQuote(
        cryptoAsset: AssetInfo,
        amount: Money,
        paymentMethodId: String? = null,
        paymentMethod: PaymentMethodType,
        brokerageQuote: BrokerageQuote
    ): Observable<BrokerageQuote> =
        Observable.interval(
            brokerageQuote.millisToExpire(),
            TimeUnit.MILLISECONDS
        ).flatMap {
            getBrokerageQuote(
                cryptoAsset = cryptoAsset,
                amount = amount,
                paymentMethodId = paymentMethodId,
                paymentMethod = paymentMethod
            )
        }.takeUntil(stopPollingBrokerageQuotes)

    private fun getPaymentMethodType(paymentMethod: PaymentMethodType) =
        // The API cannot handle GOOGLE_PAY as a payment method, so we're treating this as a card
        if (paymentMethod == PaymentMethodType.GOOGLE_PAY) PaymentMethodType.PAYMENT_CARD else paymentMethod

    private fun getPaymentMethodId(paymentMethodId: String? = null, paymentMethod: PaymentMethodType) =
        // The API cannot handle GOOGLE_PAY as a payment method, so we're sending a null paymentMethodId
        if (paymentMethod == PaymentMethodType.GOOGLE_PAY || paymentMethodId == PaymentMethod.GOOGLE_PAY_PAYMENT_ID)
            null
        else paymentMethodId

    fun cancelOrder(orderId: String): Completable = cancelOrderUseCase.invoke(orderId)

    fun getRecurringBuyFrequency(): Single<RecurringBuyFrequency> =
        rxSingle {
            mapToFrequency(
                remoteConfigRepository.getValueForFeature(rbExperimentFF.key)
                    .toString()
            )
        }

    private fun mapToFrequency(frequencyName: String): RecurringBuyFrequency {
        return when (frequencyName) {
            WEEKLY -> RecurringBuyFrequency.WEEKLY
            BIWEEKLY -> RecurringBuyFrequency.BI_WEEKLY
            MONTHLY -> RecurringBuyFrequency.MONTHLY
            else -> RecurringBuyFrequency.ONE_TIME
        }
    }

    fun createRecurringBuyOrder(
        asset: AssetInfo?,
        order: SimpleBuyOrder,
        selectedPaymentMethod: SelectedPaymentMethod?,
        recurringBuyFrequency: RecurringBuyFrequency
    ): Single<RecurringBuyOrder> {
        return if (recurringBuyFrequency != RecurringBuyFrequency.ONE_TIME) {
            require(asset != null) { "createRecurringBuyOrder selected crypto is null" }
            require(order.amount != null) { "createRecurringBuyOrder amount is null" }
            require(selectedPaymentMethod != null) { "createRecurringBuyOrder selected payment method is null" }

            val amount = order.amount
            custodialWalletManager.createRecurringBuyOrder(
                RecurringBuyRequestBody(
                    inputValue = amount.toBigInteger().toString(),
                    inputCurrency = amount.currencyCode,
                    destinationCurrency = asset.networkTicker,
                    paymentMethod = selectedPaymentMethod.paymentMethodType.name,
                    period = recurringBuyFrequency.name,
                    paymentMethodId = selectedPaymentMethod.takeUnless { it.isFunds() }?.id
                )
            )
        } else {
            Single.just(RecurringBuyOrder())
        }
    }

    fun fetchWithdrawLockTime(
        paymentMethod: PaymentMethodType,
        fiatCurrency: FiatCurrency
    ): Single<SimpleBuyIntent.WithdrawLocksTimeUpdated> =
        withdrawLocksRepository.getWithdrawLockTypeForPaymentMethod(paymentMethod, fiatCurrency)
            .map {
                SimpleBuyIntent.WithdrawLocksTimeUpdated(it)
            }.onErrorReturn {
                SimpleBuyIntent.WithdrawLocksTimeUpdated()
            }

    fun pollForKycState(): Single<SimpleBuyIntent.KycStateUpdated> =
        kycService.getTiersLegacy()
            .flatMap {
                when {
                    it.isApprovedFor(KycTier.GOLD) ->
                        simpleBuyService.isEligible()
                            .asSingle()
                            .map { eligible ->
                                if (eligible) {
                                    SimpleBuyIntent.KycStateUpdated(KycState.VERIFIED_AND_ELIGIBLE)
                                } else {
                                    SimpleBuyIntent.KycStateUpdated(KycState.VERIFIED_BUT_NOT_ELIGIBLE)
                                }
                            }
                    it.isRejectedForAny() -> Single.just(SimpleBuyIntent.KycStateUpdated(KycState.FAILED))
                    it.isInReviewForAny() -> Single.just(SimpleBuyIntent.KycStateUpdated(KycState.IN_REVIEW))
                    else -> Single.just(SimpleBuyIntent.KycStateUpdated(KycState.PENDING))
                }
            }.onErrorReturn {
                SimpleBuyIntent.KycStateUpdated(KycState.PENDING)
            }
            .repeatWhen { it.delay(INTERVAL, TimeUnit.SECONDS).zipWith(Flowable.range(0, RETRIES_SHORT)) }
            .takeUntil { it.kycState != KycState.PENDING }
            .last(SimpleBuyIntent.KycStateUpdated(KycState.PENDING))
            .map {
                if (it.kycState == KycState.PENDING) {
                    SimpleBuyIntent.KycStateUpdated(KycState.UNDECIDED)
                } else {
                    it
                }
            }

    fun updateSelectedBankAccountId(
        linkingId: String,
        providerAccountId: String = "",
        accountId: String,
        partner: BankPartner,
        action: BankTransferAction,
        source: BankAuthSource
    ): Completable {
        bankLinkingPrefs.setBankLinkingState(
            BankAuthDeepLinkState(
                bankAuthFlow = BankAuthFlowState.BANK_LINK_PENDING,
                bankLinkingInfo = BankLinkingInfo(linkingId, source)
            ).toPreferencesValue()
        )

        return bankService.updateSelectedBankAccount(
            linkingId = linkingId,
            providerAccountId = providerAccountId,
            accountId = accountId,
            attributes = providerAttributes(
                partner = partner,
                action = action,
                providerAccountId = providerAccountId,
                accountId = accountId
            )
        )
    }

    private fun providerAttributes(
        partner: BankPartner,
        providerAccountId: String,
        accountId: String,
        action: BankTransferAction
    ): BankProviderAccountAttributes =
        when (partner) {
            BankPartner.YODLEE, BankPartner.PLAID ->
                BankProviderAccountAttributes(
                    providerAccountId = providerAccountId,
                    accountId = accountId
                )
            BankPartner.YAPILY ->
                BankProviderAccountAttributes(
                    institutionId = accountId,
                    callback = bankPartnerCallbackProvider.callback(BankPartner.YAPILY, action)
                )
        }

    fun pollForBankLinkingCompleted(id: String): Single<LinkedBank> = PollService(
        bankService.getLinkedBankLegacy(id)
    ) {
        it.isLinkingInFinishedState()
    }.start(timerInSec = INTERVAL, retries = RETRIES_DEFAULT).map {
        it.value
    }

    fun pollForLinkedBankState(id: String, partner: BankPartner?): Single<PollResult<LinkedBank>> = PollService(
        bankService.getLinkedBankLegacy(id)
    ) {
        if (partner == BankPartner.YAPILY) {
            it.authorisationUrl.isNotEmpty() && it.callbackPath.isNotEmpty()
        } else {
            !it.isLinkingPending()
        }
    }.start(timerInSec = INTERVAL, retries = RETRIES_DEFAULT)

    fun checkTierLevel(): Single<SimpleBuyIntent.KycStateUpdated> {
        return kycService.getTiersLegacy().flatMap {
            when {
                it.isApprovedFor(KycTier.GOLD) -> simpleBuyService.isEligible()
                    .asSingle()
                    .map { eligible ->
                        if (eligible) {
                            SimpleBuyIntent.KycStateUpdated(KycState.VERIFIED_AND_ELIGIBLE)
                        } else {
                            SimpleBuyIntent.KycStateUpdated(KycState.VERIFIED_BUT_NOT_ELIGIBLE)
                        }
                    }
                it.isRejectedFor(KycTier.GOLD) -> Single.just(SimpleBuyIntent.KycStateUpdated(KycState.FAILED))
                it.isPendingFor(KycTier.GOLD) -> Single.just(
                    SimpleBuyIntent.KycStateUpdated(KycState.IN_REVIEW)
                )
                else -> Single.just(SimpleBuyIntent.KycStateUpdated(KycState.PENDING))
            }
        }.onErrorReturn { SimpleBuyIntent.KycStateUpdated(KycState.PENDING) }
    }

    fun linkNewBank(fiatCurrency: FiatCurrency): Single<SimpleBuyIntent.BankLinkProcessStarted> {
        return bankService.linkBank(fiatCurrency).map { linkBankTransfer ->
            SimpleBuyIntent.BankLinkProcessStarted(linkBankTransfer)
        }
    }

    private fun KycTiers.isRejectedForAny(): Boolean =
        isRejectedFor(KycTier.SILVER) ||
            isRejectedFor(KycTier.GOLD)

    private fun KycTiers.isInReviewForAny(): Boolean =
        isUnderReviewFor(KycTier.SILVER) ||
            isUnderReviewFor(KycTier.GOLD)

    fun exchangeRate(asset: AssetInfo): Single<SimpleBuyIntent.ExchangePriceWithDeltaUpdated> =
        coincore.getExchangePriceWithDelta(asset)
            .map { exchangePriceWithDelta ->
                SimpleBuyIntent.ExchangePriceWithDeltaUpdated(exchangePriceWithDelta = exchangePriceWithDelta)
            }

    fun paymentMethods(fiatCurrency: FiatCurrency): Single<PaymentMethods> =
        kycService.getTiersLegacy()
            .zipWith(
                custodialWalletManager.isSimplifiedDueDiligenceEligible().onErrorReturn { false }
                    .doOnSuccess {
                        if (it) {
                            analytics.logEventOnce(SDDAnalytics.SDD_ELIGIBLE)
                        }
                    }
            ).flatMap { (tier, sddEligible) ->
                Single.zip(
                    getAvailablePaymentMethodsTypesUseCase(
                        GetAvailablePaymentMethodsTypesUseCase.Request(
                            currency = fiatCurrency,
                            onlyEligible = tier.isInitialisedFor(KycTier.GOLD),
                            fetchSddLimits = sddEligible && tier.isInInitialState()
                        )
                    ),
                    paymentMethodService.getLinkedPaymentMethods(
                        currency = fiatCurrency
                    )
                ) { available, linked ->
                    PaymentMethods(available, linked)
                }
            }

    // attributes are null in case of bank
    fun confirmOrder(
        orderId: String,
        paymentMethodId: String?,
        attributes: SimpleBuyConfirmationAttributes?,
        isBankPartner: Boolean?
    ): Single<BuySellOrder> {
        return custodialWalletManager.confirmOrder(
            orderId,
            attributes,
            paymentMethodId,
            isBankPartner
        )
    }

    fun createOrder(
        cryptoAsset: AssetInfo,
        amount: Money,
        paymentMethodId: String? = null,
        paymentMethodType: PaymentMethodType,
        recurringBuyFrequency: RecurringBuyFrequency?,
        quote: BuyQuote
    ): Single<BuySellOrder> {
        return custodialWalletManager.createOrder(
            custodialWalletOrder = CustodialWalletOrder(
                quoteId = quote.id,
                pair = "${cryptoAsset.networkTicker}-${amount.currencyCode}",
                action = Product.BUY.name,
                input = OrderInput(
                    amount.currencyCode, amount.toBigInteger().toString()
                ),
                output = OrderOutput(
                    cryptoAsset.networkTicker, null
                ),
                paymentMethodId = getPaymentMethodId(paymentMethodId, paymentMethodType),
                paymentType = getPaymentMethodType(paymentMethodType).name,
                period = recurringBuyFrequency?.name
            ),
            stateAction = "pending"
        )
    }

    fun pollForOrderStatus(orderId: String): Single<PollResult<BuySellOrder>> =
        cardPaymentAsyncFF.enabled.flatMap { isCardPaymentAsyncEnabled ->
            PollService(custodialWalletManager.getBuyOrder(orderId)) {
                it.state == OrderState.FINISHED ||
                    it.state == OrderState.FAILED ||
                    it.state == OrderState.CANCELED ||
                    (isCardPaymentAsyncEnabled && it.canFinishPollingForAsyncCardPayments())
            }.start(INTERVAL, RETRIES_SHORT)
        }

    private fun BuySellOrder.canFinishPollingForAsyncCardPayments() =
        (paymentMethodType == PaymentMethodType.PAYMENT_CARD || paymentMethodType == PaymentMethodType.GOOGLE_PAY) &&
            attributes != null

    fun pollForAuthorisationUrl(orderId: String): Single<PollResult<BuySellOrder>> =
        PollService(
            custodialWalletManager.getBuyOrder(orderId)
        ) {
            it.attributes?.authorisationUrl != null
        }.start()

    fun pollForCardStatus(cardId: String): Single<CardIntent.CardUpdated> =
        PollService(
            cardService.getCardDetailsLegacy(cardId)
        ) {
            it.status == CardStatus.BLOCKED ||
                it.status == CardStatus.EXPIRED ||
                it.status == CardStatus.ACTIVE
        }
            .start()
            .map {
                CardIntent.CardUpdated(it.value)
            }

    fun eligiblePaymentMethodsTypes(fiatCurrency: FiatCurrency): Single<List<EligiblePaymentMethodType>> =
        paymentMethodService.getEligiblePaymentMethodTypes(fiatCurrency = fiatCurrency)

    fun getLinkedBankInfo(paymentMethodId: String) =
        bankService.getLinkedBankLegacy(paymentMethodId)

    fun fetchOrder(orderId: String) = custodialWalletManager.getBuyOrder(orderId)

    fun addNewCard(
        cardData: CardData,
        fiatCurrency: FiatCurrency,
        billingAddress: BillingAddress
    ): Single<CardToBeActivated> =
        addCardWithPaymentTokens(cardData, fiatCurrency, billingAddress)

    private fun addCardWithPaymentTokens(
        cardData: CardData,
        fiatCurrency: FiatCurrency,
        billingAddress: BillingAddress
    ) = custodialWalletManager.getCardAcquirers().flatMap { cardAcquirers ->
        rxSingle {
            // The backend is expecting a map of account codes and payment tokens.
            // Given that custodialWalletManager.getCardAcquirers() returns a list of PaymentCardAcquirers,
            // we need to map the PaymentCardAcquirers into payment tokens (string).
            cardAcquirers.associateWith { acquirer -> getPaymentToken(acquirer, cardData, billingAddress) }
        }.flatMap { acquirerTokenMap ->
            val acquirerAccountCodeTokensMap = acquirerTokenMap.filterValues { token ->
                token.isNotEmpty()
            }
                .flatMap { (acquirer, paymentToken) ->
                    acquirer.cardAcquirerAccountCodes.map { accountCode ->
                        accountCode to paymentToken
                    }
                }
                .associate { (accountCode, paymentToken) ->
                    accountCode to paymentToken
                }
            cardService.addNewCard(fiatCurrency, billingAddress, acquirerAccountCodeTokensMap)
        }
    }

    private suspend fun getPaymentToken(
        acquirer: PaymentCardAcquirer,
        cardData: CardData,
        billingAddress: BillingAddress
    ) = cardProcessors[CardAcquirer.fromString(acquirer.cardAcquirerName)]?.createPaymentMethod(
        cardDetails = cardData.toCardDetails(),
        billingAddress = billingAddress.toCardBillingAddress(),
        apiKey = acquirer.apiKey
    )?.doOnFailure { Timber.e(it.throwable) }
        ?.getOrDefault(EMPTY_PAYMENT_TOKEN)
        ?: EMPTY_PAYMENT_TOKEN

    fun updateApprovalStatus(callbackPath: String) {
        bankLinkingPrefs.getBankLinkingState().fromPreferencesValue()?.let {
            bankLinkingPrefs.setBankLinkingState(
                it.copy(bankAuthFlow = BankAuthFlowState.BANK_APPROVAL_PENDING).toPreferencesValue()
            )
        } ?: run {
            bankLinkingPrefs.setBankLinkingState(
                BankAuthDeepLinkState(bankAuthFlow = BankAuthFlowState.BANK_APPROVAL_PENDING).toPreferencesValue()
            )
        }

        val sanitisedUrl = callbackPath.removePrefix("nabu-gateway/")
        bankLinkingPrefs.setDynamicOneTimeTokenUrl(sanitisedUrl)
    }

    fun updateOneTimeTokenPath(callbackPath: String) {
        val sanitisedUrl = callbackPath.removePrefix("nabu-gateway/")
        bankLinkingPrefs.setDynamicOneTimeTokenUrl(sanitisedUrl)
    }

    fun initializeFeatureFlags(): Single<FeatureFlagsSet> {
        return Single.zip(
            buyQuoteRefreshFF.enabled,
            plaidFF.enabled,
            rbFrequencySuggestionFF.enabled,
            rbExperimentFF.enabled,
            feynmanEnterAmountFF.enabled,
            feynmanCheckoutFF.enabled,
            improvedPaymentUxFF.enabled
        ) { buyQuoteRefreshFF, plaidFF, rbFrequencySuggestionFF, rbExperimentFF,
            feynmanEnterAmountFF, feynmanCheckoutFF, improvedPaymentUxFF ->
            FeatureFlagsSet(
                buyQuoteRefreshFF = buyQuoteRefreshFF,
                plaidFF = plaidFF,
                rbFrequencySuggestionFF = rbFrequencySuggestionFF,
                rbExperimentFF = rbExperimentFF,
                feynmanEnterAmountFF = feynmanEnterAmountFF,
                feynmanCheckoutFF = feynmanCheckoutFF,
                improvedPaymentUxFF = improvedPaymentUxFF
            )
        }
    }

    fun getGooglePayInfo(
        currency: FiatCurrency
    ): Single<SimpleBuyIntent.GooglePayInfoReceived> =
        cardService.getGooglePayTokenizationParameters(
            currency = currency.networkTicker
        ).map {
            SimpleBuyIntent.GooglePayInfoReceived(
                tokenizationData = Json.decodeFromString(StringMapSerializer, it.googlePayParameters),
                beneficiaryId = it.beneficiaryID,
                merchantBankCountryCode = it.merchantBankCountryCode,
                allowPrepaidCards = it.allowPrepaidCards ?: true,
                allowCreditCards = it.allowCreditCards ?: true,
                allowedAuthMethods = it.allowedAuthMethods ?: defaultAllowedAuthMethods,
                allowedCardNetworks = it.allowedCardNetworks ?: defaultAllowedCardNetworks,
                billingAddressRequired = it.billingAddressRequired ?: true,
                billingAddressParameters = BillingAddressParameters(
                    format = it.billingAddressParameters.format ?: BillingAddressParameters().format,
                    phoneNumberRequired = it.billingAddressParameters.phoneNumberRequired
                        ?: BillingAddressParameters().phoneNumberRequired
                )
            )
        }

    fun loadLinkedCards(): Single<List<LinkedPaymentMethod.Card>> =
        paymentsRepository.getLinkedCardsLegacy(
            CardStatus.PENDING,
            CardStatus.ACTIVE
        )

    fun getLastPaymentMethodId() =
        simpleBuyPrefs.getLastPaymentMethodId().ifEmpty { null }

    fun shouldShowAppRating() =
        simpleBuyPrefs.buysCompletedCount >= APP_RATING_MINIMUM_BUY_ORDERS

    fun checkNewCardRejectionRate(binNumber: String): Single<CardRejectionState> =
        rxSingle {
            paymentsRepository.checkNewCardRejectionState(binNumber).getOrDefault(CardRejectionState.NotRejected)
        }

    data class PaymentMethods(
        val available: List<AvailablePaymentMethodType>,
        val linked: List<LinkedPaymentMethod>
    )

    private fun CardData.toCardDetails() =
        CardDetails(
            number = number,
            expMonth = month,
            expYear = year,
            cvc = cvv,
            fullName = fullName
        )

    private fun BillingAddress.toCardBillingAddress() =
        CardBillingAddress(
            city = city,
            country = countryCode,
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            postalCode = postCode,
            state = state
        )

    fun updateCountersForCompletedOrders() {
        simpleBuyPrefs.hasCompletedAtLeastOneBuy = true
        simpleBuyPrefs.buysCompletedCount += 1
        onboardingPrefs.isLandingCtaDismissed = true
    }

    fun getPrefillAndQuickFillAmounts(
        limits: TxLimits,
        assetCode: String,
        fiatCurrency: FiatCurrency,
        prepopulatedAmountFromDeeplink: Boolean,
        prepopulatedAmount: Money
    ): Single<Pair<Money, QuickFillButtonData?>> =
        quickFillRoundingService.getQuickFillRoundingForAction(AssetAction.Buy).map { roundingInfo ->

            val amountString = simpleBuyPrefs.getLastAmount("$assetCode-${fiatCurrency.networkTicker}")
            val listOfAmounts = mutableListOf<Money>()

            var prefilledAmount = when {
                prepopulatedAmountFromDeeplink -> prepopulatedAmount
                amountString.isEmpty() -> {
                    Money.fromMajor(fiatCurrency, BigDecimal(DEFAULT_MIN_PREFILL_AMOUNT))
                }
                amountString.isNotEmpty() -> Money.fromMajor(fiatCurrency, BigDecimal(amountString))
                else -> Money.fromMajor(fiatCurrency, BigDecimal.ZERO)
            }

            val isMaxLimited = limits.isAmountOverMax(prefilledAmount)
            val isMinLimited = limits.isAmountUnderMin(prefilledAmount)

            prefilledAmount = when {
                isMinLimited && isMaxLimited -> Money.fromMajor(fiatCurrency, BigDecimal.ZERO)
                isMinLimited -> limits.minAmount
                isMaxLimited -> limits.maxAmount
                else -> prefilledAmount
            }

            roundingInfo.forEachIndexed { index, data ->
                val roundingData = data as QuickFillRoundingData.BuyRoundingData
                val multiplier = roundingData.multiplier.toFloat()
                val lastAmount = if (index == 0) {
                    prefilledAmount.times(multiplier)
                } else if (listOfAmounts.size >= index) {
                    listOfAmounts[index - 1].times(multiplier)
                } else {
                    Money.zero(fiatCurrency)
                }

                val prefillAmount = roundToNearest(
                    lastAmount = lastAmount,
                    nearest = roundingData.rounding
                )

                if (limits.isAmountInRange(prefillAmount)) {
                    listOfAmounts.add(prefillAmount)
                }
            }

            val quickFillButtonData = QuickFillButtonData(
                maxAmount = (limits.max as? TxLimit.Limited)?.amount ?: Money.zero(fiatCurrency),
                quickFillButtons = listOfAmounts.map { amount ->
                    QuickFillDisplayAndAmount(
                        displayValue = amount.toStringWithSymbol(includeDecimalsWhenWhole = false),
                        amount = amount,
                        position = listOfAmounts.indexOf(amount)
                    )
                }
            )

            return@map Pair(prefilledAmount, quickFillButtonData)
        }

    fun getListOfStates(countryCode: String): Single<List<Region.State>> =
        rxSingleOutcome(Schedulers.io().asCoroutineDispatcher()) {
            eligibilityService.getStatesList(countryCode, GetRegionScope.None)
        }

    fun saveOrderAmountAndPaymentMethodId(pair: String, amount: String, paymentId: String) {
        simpleBuyPrefs.setLastAmount(
            pair = pair,
            amount = amount
        )
        simpleBuyPrefs.setLastPaymentMethodId(paymentMethodId = paymentId)
    }

    private fun roundToNearest(lastAmount: Money, nearest: Int): Money {
        return Money.fromMajor(
            lastAmount.currency, (nearest * (floor(lastAmount.toFloat() / nearest))).toBigDecimal()
        )
    }

    companion object {
        private const val WEEKLY = "WEEKLY"
        private const val BIWEEKLY = "BIWEEKLY"
        private const val MONTHLY = "MONTHLY"

        const val PENDING = "pending"

        private const val INTERVAL_QUOTE_PRICE = 5000L
        private const val SIMPLEBUY_PROFILE_NAME = "SIMPLEBUY"

        private const val INTERVAL: Long = 5
        private const val RETRIES_SHORT = 6
        private const val RETRIES_DEFAULT = 12
        private const val EMPTY_PAYMENT_TOKEN: PaymentToken = ""

        private const val DEFAULT_MIN_PREFILL_AMOUNT = "50"
    }
}
