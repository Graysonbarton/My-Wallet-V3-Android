package piuk.blockchain.android.ui.dashboard.announcements

import com.blockchain.api.paymentmethods.models.PaymentMethodResponse
import com.blockchain.api.services.PaymentMethodsService
import com.blockchain.coincore.AccountBalance
import com.blockchain.coincore.AccountGroup
import com.blockchain.coincore.Coincore
import com.blockchain.coincore.CryptoAccount
import com.blockchain.coincore.SingleAccountList
import com.blockchain.core.kyc.domain.KycService
import com.blockchain.core.kyc.domain.model.KycLimits
import com.blockchain.core.kyc.domain.model.KycTier
import com.blockchain.core.kyc.domain.model.KycTierDetail
import com.blockchain.core.kyc.domain.model.KycTierState
import com.blockchain.core.kyc.domain.model.KycTiers
import com.blockchain.core.kyc.domain.model.TiersMap
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.core.price.Prices24HrWithDelta
import com.blockchain.domain.experiments.RemoteConfigService
import com.blockchain.domain.fiatcurrencies.FiatCurrenciesService
import com.blockchain.featureflag.FeatureFlag
import com.blockchain.nabu.Feature
import com.blockchain.nabu.UserIdentity
import com.blockchain.nabu.api.getuser.domain.UserService
import com.blockchain.payments.googlepay.manager.GooglePayManager
import com.blockchain.preferences.CurrencyPrefs
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.ExchangeRate
import info.blockchain.balance.FiatCurrency
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.simplebuy.SimpleBuyState
import piuk.blockchain.android.simplebuy.SimpleBuySyncFactory
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementQueries.Companion.NEW_ASSET_TICKER
import piuk.blockchain.android.ui.tiers

class AnnouncementQueriesTest {

    private val userService: UserService = mock()
    private val kycService: KycService = mock()
    private val userIdentity: UserIdentity = mock()
    private val coincore: Coincore = mock()
    private val assetCatalogue: AssetCatalogue = mock()
    private val remoteConfigService: RemoteConfigService = mock()
    private val googlePayManager: GooglePayManager = mock()
    private val googlePayEnabledFlag: FeatureFlag = mock()
    private val paymentMethodsService: PaymentMethodsService = mock()
    private val fiatCurrenciesService: FiatCurrenciesService = mock()
    private val exchangeRatesDataManager: ExchangeRatesDataManager = mock()
    private val currencyPrefs: CurrencyPrefs = mock()
    private val hideDustFF: FeatureFlag = mock()

    private val sbSync: SimpleBuySyncFactory = mock()

    private lateinit var subject: AnnouncementQueries

    @Before
    fun setUp() {
        subject = spy(
            AnnouncementQueries(
                userService = userService,
                kycService = kycService,
                sbStateFactory = sbSync,
                userIdentity = userIdentity,
                coincore = coincore,
                assetCatalogue = assetCatalogue,
                remoteConfigService = remoteConfigService,
                googlePayManager = googlePayManager,
                googlePayEnabledFlag = googlePayEnabledFlag,
                paymentMethodsService = paymentMethodsService,
                fiatCurrenciesService = fiatCurrenciesService,
                exchangeRatesDataManager = exchangeRatesDataManager,
                currencyPrefs = currencyPrefs,
                hideDustFF = hideDustFF
            )
        )
    }

    @Test
    fun `asset ticker raw json is empty`() {
        whenever(remoteConfigService.getRawJson(NEW_ASSET_TICKER)).thenReturn(Single.just(""))
        whenever(assetCatalogue.fromNetworkTicker(any())).thenReturn(null)

        subject.getAssetFromCatalogue().test().assertComplete()
    }

    @Test
    fun `asset ticker raw json doesn't exist`() {
        val testException = Throwable()
        whenever(remoteConfigService.getRawJson(NEW_ASSET_TICKER)).thenReturn(Single.error(testException))

        subject.getAssetFromCatalogue().test().assertError(testException)
    }

    @Test
    fun `asset ticker raw json returns unknown ticker`() {
        val moonToken = "TTM"
        whenever(remoteConfigService.getRawJson(NEW_ASSET_TICKER)).thenReturn(Single.just(moonToken))
        whenever(assetCatalogue.fromNetworkTicker(moonToken)).thenReturn(null)

        subject.getAssetFromCatalogue().test().assertComplete()
    }

    @Test
    fun `asset ticker raw json returns known ticker`() {
        whenever(remoteConfigService.getRawJson(NEW_ASSET_TICKER))
            .thenReturn(Single.just(CryptoCurrency.BTC.networkTicker))
        whenever(assetCatalogue.assetInfoFromNetworkTicker(CryptoCurrency.BTC.networkTicker))
            .thenReturn(CryptoCurrency.BTC)

        subject.getAssetFromCatalogue().test().assertValue(CryptoCurrency.BTC)
    }

    @Test
    fun `isTier1Or2Verified returns true for tier1 verified`() {

        whenever(kycService.getTiersLegacy()).thenReturn(
            Single.just(
                KycTiers(
                    TiersMap(
                        mapOf(
                            KycTier.BRONZE to
                                KycTierDetail(
                                    KycTierState.None,
                                    KycLimits(null, null)
                                ),
                            KycTier.SILVER to
                                KycTierDetail(
                                    KycTierState.Verified,
                                    KycLimits(null, null)
                                ),
                            KycTier.GOLD to
                                KycTierDetail(
                                    KycTierState.None,
                                    KycLimits(null, null)
                                )
                        )
                    )
                )
            )
        )

        subject.isTier1Or2Verified()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isTier1Or2Verified returns true for tier2 verified`() {
        whenever(kycService.getTiersLegacy()).thenReturn(
            Single.just(
                KycTiers(
                    TiersMap(
                        mapOf(
                            KycTier.BRONZE to
                                KycTierDetail(
                                    KycTierState.None,
                                    KycLimits(null, null)
                                ),
                            KycTier.SILVER to
                                KycTierDetail(
                                    KycTierState.Verified,
                                    KycLimits(null, null)
                                ),
                            KycTier.GOLD to
                                KycTierDetail(
                                    KycTierState.Verified,
                                    KycLimits(null, null)
                                )
                        )
                    )
                )
            )
        )

        subject.isTier1Or2Verified()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isTier1Or2Verified returns false if not verified`() {
        whenever(kycService.getTiersLegacy()).thenReturn(
            Single.just(
                KycTiers(
                    TiersMap(
                        mapOf(
                            KycTier.BRONZE to
                                KycTierDetail(
                                    KycTierState.None,
                                    KycLimits(null, null)
                                ),
                            KycTier.SILVER to
                                KycTierDetail(
                                    KycTierState.None,
                                    KycLimits(null, null)
                                ),
                            KycTier.GOLD to
                                KycTierDetail(
                                    KycTierState.None,
                                    KycLimits(null, null)
                                )
                        )

                    )
                )
            )
        )

        subject.isTier1Or2Verified()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - no local simple buy state exists, return false`() {
        whenever(sbSync.currentState()).thenReturn(null)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - local simple buy state exists but has finished kyc, return false`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(false)
        whenever(kycService.getTiersLegacy()).thenReturn(
            Single.just(tiers(KycTierState.Verified, KycTierState.Verified))
        )
        whenever(sbSync.currentState()).thenReturn(state)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - local simple buy state exists and has finished kyc, return true`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(true)
        whenever(state.kycVerificationState).thenReturn(null)

        whenever(kycService.getTiersLegacy()).thenReturn(
            Single.just(tiers(KycTierState.Verified, KycTierState.None))
        )
        whenever(sbSync.currentState()).thenReturn(state)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - simple buy state is not finished, and kyc state is pending - as expected`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(true)
        whenever(kycService.getTiersLegacy()).thenReturn(Single.just(tiers(KycTierState.Pending, KycTierState.None)))
        whenever(sbSync.currentState()).thenReturn(state)
        whenever(kycService.getTiersLegacy()).thenReturn(Single.just(tiers(KycTierState.Pending, KycTierState.None)))

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { it }
            .assertValueCount(1)
            .assertComplete()
    }

    // Belt and braces checks: add double check that the SB state doesn't think kyc data has been submitted
    // to patch AND-2790, 2801. This _may_ be insufficient, though. If it doesn't solve the problem, we may have to
    // check backend kyc state ourselves...

    @Test
    fun `isSimpleBuyKycInProgress - SB state reports unfinished, but kyc docs are submitted - belt & braces case`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(true)

        whenever(kycService.getTiersLegacy()).thenReturn(
            Single.just(tiers(KycTierState.Pending, KycTierState.UnderReview))
        )
        whenever(sbSync.currentState()).thenReturn(state)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `isSimpleBuyKycInProgress - SB state reports unfinished, but kyc docs are submitted - belt & braces case 2`() {
        val state: SimpleBuyState = mock()
        whenever(state.kycStartedButNotCompleted).thenReturn(true)
        whenever(kycService.getTiersLegacy()).thenReturn(
            Single.just(tiers(KycTierState.Pending, KycTierState.Verified))
        )
        whenever(sbSync.currentState()).thenReturn(state)

        subject.isSimpleBuyKycInProgress()
            .test()
            .assertValue { !it }
            .assertValueCount(1)
            .assertComplete()
    }

    @Test
    fun `user isSddEligible but verified`() {
        whenever(userIdentity.isEligibleFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(true))
        whenever(userIdentity.isVerifiedFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(true))

        subject.isSimplifiedDueDiligenceEligibleAndNotVerified()
            .test()
            .assertValue { !it }
    }

    @Test
    fun `user not SddEligible neither verified`() {
        whenever(userIdentity.isEligibleFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(false))
        whenever(userIdentity.isVerifiedFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(false))

        subject.isSimplifiedDueDiligenceEligibleAndNotVerified()
            .test()
            .assertValue { !it }
    }

    @Test
    fun `user SddEligible and not verified`() {
        whenever(userIdentity.isEligibleFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(true))
        whenever(userIdentity.isVerifiedFor(Feature.SimplifiedDueDiligence)).thenReturn(Single.just(false))

        subject.isSimplifiedDueDiligenceEligibleAndNotVerified()
            .test()
            .assertValue { it }
    }

    @Test
    fun `when google pay feature flag disabled then return false`() {
        whenever(googlePayEnabledFlag.enabled).thenReturn(Single.just(false))
        whenever(subject.checkGooglePayAvailability()).thenReturn(Single.just(true))
        whenever(fiatCurrenciesService.selectedTradingCurrency).thenReturn(FiatCurrency.Dollars)
        whenever(
            paymentMethodsService.getAvailablePaymentMethodsTypes(
                FiatCurrency.Dollars.networkTicker, null, true
            )
        ).thenReturn(
            Single.just(
                listOf(
                    PaymentMethodResponse(
                        type = GOOGLE_PAY,
                        eligible = true,
                        visible = true,
                        limits = mock(),
                        subTypes = mock(),
                        currency = FiatCurrency.Dollars.networkTicker,
                        mobilePayment = listOf(GOOGLE_PAY)
                    )
                )
            )
        )

        subject.isGooglePayAvailable().test().assertValue {
            !it
        }
    }

    @Test
    fun `when goggle pay not a supported payment method then return false`() {
        whenever(googlePayEnabledFlag.enabled).thenReturn(Single.just(true))
        whenever(subject.checkGooglePayAvailability()).thenReturn(Single.just(true))
        whenever(fiatCurrenciesService.selectedTradingCurrency).thenReturn(FiatCurrency.Dollars)
        whenever(
            paymentMethodsService.getAvailablePaymentMethodsTypes(
                FiatCurrency.Dollars.networkTicker, null, true
            )
        ).thenReturn(Single.just(emptyList()))

        subject.isGooglePayAvailable().test().assertValue {
            !it
        }
    }

    @Test
    fun `when google pay not supported by device then return false`() {
        whenever(googlePayEnabledFlag.enabled).thenReturn(Single.just(true))
        whenever(subject.checkGooglePayAvailability()).thenReturn(Single.just(false))
        whenever(fiatCurrenciesService.selectedTradingCurrency).thenReturn(FiatCurrency.Dollars)
        whenever(
            paymentMethodsService.getAvailablePaymentMethodsTypes(
                FiatCurrency.Dollars.networkTicker, null, true
            )
        ).thenReturn(
            Single.just(
                listOf(
                    PaymentMethodResponse(
                        type = GOOGLE_PAY,
                        eligible = true,
                        visible = true,
                        limits = mock(),
                        subTypes = mock(),
                        currency = FiatCurrency.Dollars.networkTicker,
                        mobilePayment = listOf(GOOGLE_PAY)
                    )
                )
            )
        )

        subject.isGooglePayAvailable().test().assertValue {
            !it
        }
    }

    @Test
    fun `when google pay flag enabled and a supported payment method and supported by device then return true`() {
        whenever(googlePayEnabledFlag.enabled).thenReturn(Single.just(true))
        whenever(subject.checkGooglePayAvailability()).thenReturn(Single.just(true))
        whenever(fiatCurrenciesService.selectedTradingCurrency).thenReturn(FiatCurrency.Dollars)
        whenever(
            paymentMethodsService.getAvailablePaymentMethodsTypes(
                FiatCurrency.Dollars.networkTicker, null, true
            )
        ).thenReturn(
            Single.just(
                listOf(
                    PaymentMethodResponse(
                        type = GOOGLE_PAY,
                        eligible = true,
                        visible = true,
                        limits = mock(),
                        subTypes = mock(),
                        currency = FiatCurrency.Dollars.networkTicker,
                        mobilePayment = listOf(GOOGLE_PAY)
                    )
                )
            )
        )

        subject.isGooglePayAvailable().test().assertValue {
            it
        }
    }

    @Test
    fun `asset price returns price`() {
        val asset = CryptoCurrency.BTC
        val prices24HrWithDelta = Prices24HrWithDelta(
            0.0,
            ExchangeRate.zeroRateExchangeRate(asset, FiatCurrency.Dollars),
            ExchangeRate.zeroRateExchangeRate(asset, FiatCurrency.Dollars)
        )
        whenever(currencyPrefs.selectedFiatCurrency)
            .thenReturn(FiatCurrency.Dollars)
        whenever(exchangeRatesDataManager.getPricesWith24hDeltaLegacy(asset, FiatCurrency.Dollars))
            .thenReturn(Observable.just(prices24HrWithDelta))

        subject.getAssetPrice(asset).test().assertValue(prices24HrWithDelta)
    }

    @Test
    fun `given dust ff is off when query is checked then don't show`() {
        whenever(hideDustFF.enabled).thenReturn(Single.just(false))

        val test = subject.hasDustBalances().test()
        test.assertValue {
            !it
        }

        verify(hideDustFF).enabled
        verifyNoMoreInteractions(coincore)
        verifyNoMoreInteractions(hideDustFF)
    }

    @Test
    fun `given dust ff is on and no dust wallets when query is checked then don't show`() {
        whenever(hideDustFF.enabled).thenReturn(Single.just(true))
        val noDustFiat: Money = mock {
            on { isDust() }.thenReturn(false)
        }
        val noDustBalance: AccountBalance = mock {
            on { totalFiat }.thenReturn(noDustFiat)
        }
        val accountsList: SingleAccountList = listOf(
            mock<CryptoAccount> {
                on { balanceRx() }.thenReturn(Observable.just(noDustBalance))
            },
            mock<CryptoAccount> {
                on { balanceRx() }.thenReturn(Observable.just(noDustBalance))
            }
        )
        val accountGroup: AccountGroup = mock {
            on { accounts }.thenReturn(accountsList)
        }
        whenever(coincore.allWallets(includeArchived = false)).thenReturn(Single.just(accountGroup))

        val test = subject.hasDustBalances().test()
        test.assertValue {
            it == false
        }

        verify(hideDustFF).enabled
        verify(coincore).allWallets(includeArchived = false)

        verifyNoMoreInteractions(coincore)
        verifyNoMoreInteractions(hideDustFF)
    }

    @Test
    fun `given dust ff is on and some dust wallets when query is checked then show`() {
        whenever(hideDustFF.enabled).thenReturn(Single.just(true))
        val noDustFiat: Money = mock {
            on { isDust() }.thenReturn(false)
        }
        val noDustBalance: AccountBalance = mock {
            on { totalFiat }.thenReturn(noDustFiat)
        }

        val dustFiat: Money = mock {
            on { isDust() }.thenReturn(true)
        }
        val dustBalance: AccountBalance = mock {
            on { totalFiat }.thenReturn(dustFiat)
        }
        val accountsList: SingleAccountList = listOf(
            mock<CryptoAccount> {
                on { balanceRx() }.thenReturn(Observable.just(dustBalance))
            },
            mock<CryptoAccount> {
                on { balanceRx() }.thenReturn(Observable.just(noDustBalance))
            }
        )
        val accountGroup: AccountGroup = mock {
            on { accounts }.thenReturn(accountsList)
        }
        whenever(coincore.allWallets(includeArchived = false)).thenReturn(Single.just(accountGroup))

        val test = subject.hasDustBalances().test()
        test.assertValue {
            it == true
        }

        verify(hideDustFF).enabled
        verify(coincore).allWallets(includeArchived = false)

        verifyNoMoreInteractions(coincore)
        verifyNoMoreInteractions(hideDustFF)
    }

    companion object {
        private const val GOOGLE_PAY = "GOOGLE_PAY"
    }
}
