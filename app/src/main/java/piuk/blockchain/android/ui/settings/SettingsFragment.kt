package piuk.blockchain.android.ui.settings

import android.content.pm.ShortcutManager
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.blockchain.analytics.events.AnalyticsEvents
import com.blockchain.commonarch.presentation.base.updateToolbar
import com.blockchain.commonarch.presentation.mvi.MviFragment
import com.blockchain.componentlib.alert.BlockchainSnackbar
import com.blockchain.componentlib.alert.SnackbarType
import com.blockchain.componentlib.basic.ComposeColors
import com.blockchain.componentlib.basic.ComposeGravities
import com.blockchain.componentlib.basic.ComposeTypographies
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.button.MinimalButtonView
import com.blockchain.componentlib.navigation.NavigationBarButton
import com.blockchain.componentlib.tablerow.BalanceTableRowView
import com.blockchain.componentlib.tablerow.DefaultTableRowView
import com.blockchain.componentlib.tag.TagType
import com.blockchain.componentlib.tag.TagViewState
import com.blockchain.componentlib.viewextensions.gone
import com.blockchain.componentlib.viewextensions.goneIf
import com.blockchain.componentlib.viewextensions.visible
import com.blockchain.componentlib.viewextensions.visibleIf
import com.blockchain.core.kyc.domain.model.KycTier
import com.blockchain.domain.paymentmethods.model.CardRejectionState
import com.blockchain.domain.paymentmethods.model.PaymentMethod
import com.blockchain.domain.paymentmethods.model.PaymentMethodType
import com.blockchain.domain.referral.model.ReferralInfo
import com.blockchain.enviroment.EnvironmentConfig
import com.blockchain.nabu.BasicProfileInfo
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.presentation.koin.scopedInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.koin.android.ext.android.inject
import piuk.blockchain.android.BuildConfig
import piuk.blockchain.android.R
import piuk.blockchain.android.cards.CardDetailsActivity
import piuk.blockchain.android.cards.RemoveCardBottomSheet
import piuk.blockchain.android.cards.mapper.icon
import piuk.blockchain.android.databinding.FragmentRedesignSettingsBinding
import piuk.blockchain.android.domain.usecases.LinkAccess
import piuk.blockchain.android.simplebuy.SimpleBuyAnalytics
import piuk.blockchain.android.simplebuy.sheets.RemoveLinkedBankBottomSheet
import piuk.blockchain.android.ui.base.ErrorButtonCopies
import piuk.blockchain.android.ui.base.ErrorDialogData
import piuk.blockchain.android.ui.base.ErrorSlidingBottomDialog
import piuk.blockchain.android.ui.linkbank.BankAuthActivity
import piuk.blockchain.android.ui.linkbank.BankAuthSource
import piuk.blockchain.android.ui.linkbank.alias.BankAliasLinkContract
import piuk.blockchain.android.ui.referral.presentation.Origin
import piuk.blockchain.android.ui.referral.presentation.ReferralAnalyticsEvents
import piuk.blockchain.android.ui.settings.sheets.AddPaymentMethodsBottomSheet
import piuk.blockchain.android.util.AndroidUtils

class SettingsFragment :
    MviFragment<SettingsModel, SettingsIntent, SettingsState, FragmentRedesignSettingsBinding>(),
    AddPaymentMethodsBottomSheet.Host,
    RemoveCardBottomSheet.Host,
    RemoveLinkedBankBottomSheet.Host,
    SettingsScreen,
    ErrorSlidingBottomDialog.Host {

    interface Host {
        fun updateBasicProfile(basicProfileInfo: BasicProfileInfo)
        fun updateTier(tier: KycTier)
    }

    val host: Host by lazy {
        activity as? Host
            ?: throw IllegalStateException("Host activity is not a SettingsFragment.Host")
    }

    private val onCardAddedResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == AppCompatActivity.RESULT_OK) {
            model.process(SettingsIntent.LoadPaymentMethods)
        }
    }

    private val onBankTransferAddedResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == AppCompatActivity.RESULT_OK) {
                model.process(SettingsIntent.LoadPaymentMethods)
            }
        }

    private val environmentConfig: EnvironmentConfig by inject()
    private val currencyPrefs: CurrencyPrefs by inject()

    override val model: SettingsModel by scopedInject()

    private val bankAliasLinkLauncher = registerForActivityResult(BankAliasLinkContract()) {}

    override fun initBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentRedesignSettingsBinding =
        FragmentRedesignSettingsBinding.inflate(inflater, container, false)

    override fun navigator(): SettingsNavigator =
        (activity as? SettingsNavigator) ?: throw IllegalStateException(
            "Parent must implement SettingsNavigator"
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appVersion.text = getString(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        binding.legalEntity.text = getString(R.string.legal_entity_copyright, currentYear)
    }

    override fun onResume() {
        super.onResume()
        model.process(SettingsIntent.InitializeFeatureFlags)
        model.process(SettingsIntent.LoadHeaderInformation)
        model.process(SettingsIntent.LoadPaymentMethods)

        updateToolbar(
            toolbarTitle = getString(R.string.toolbar_settings),
            menuItems = listOf(
                NavigationBarButton.Icon(
                    drawable = R.drawable.ic_support_chat,
                    contentDescription = R.string.accessibility_support
                ) {
                    analytics.logEvent(AnalyticsEvents.Support)
                    navigator().goToSupportCentre()
                }
            )
        )
    }

    override fun render(newState: SettingsState) {
        setupMenuItems(newState.basicProfileInfo)
        host.updateTier(newState.tier)
        newState.basicProfileInfo?.let { userInfo ->
            setInfoHeader(userInfo, newState.tier)
            host.updateBasicProfile(userInfo)
        } ?: setupEmptyHeader()

        showUserTierIcon(newState.tier)

        if (newState.viewToLaunch != ViewToLaunch.None) {
            renderView(newState)
        }

        if (newState.hasWalletUnpaired) {
            analytics.logEvent(AnalyticsEvents.Logout)
            if (AndroidUtils.is25orHigher()) {
                requireActivity().getSystemService(ShortcutManager::class.java).removeAllDynamicShortcuts()
            }
        }

        if (newState.paymentMethodInfo != null) {
            binding.paymentsContainer.removeAllViews()

            addPaymentMethods(
                paymentMethodInfo = newState.paymentMethodInfo,
                totalLinkedPaymentMethods = newState.paymentMethodInfo.linkedBanks.count() +
                    newState.paymentMethodInfo.linkedCards.count(),
                isUserGold = newState.tier == KycTier.GOLD,
                canPayWithBind = newState.canPayWithBind
            )
        } else {
            with(binding.paymentsContainer) {
                removeAllViews()
                addView(
                    LottieAnimationView(requireContext()).apply {
                        imageAssetsFolder = LOTTIE_LOADER_PATH
                        setAnimation(LOTTIE_LOADER_PATH)
                        repeatMode = LottieDrawable.RESTART
                        playAnimation()
                    },
                    LinearLayoutCompat.LayoutParams(
                        resources.getDimensionPixelOffset(R.dimen.xlarge_spacing),
                        resources.getDimensionPixelOffset(R.dimen.xlarge_spacing)
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                )
            }
        }

        with(binding.referralBtn) {
            if (newState.referralInfo is ReferralInfo.Data) {
                visible()
                onClick = {
                    analytics.logEvent(ReferralAnalyticsEvents.ReferralProgramClicked(Origin.Profile))
                    navigator().goToReferralCode()
                }

                newState.referralInfo.announcementInfo?.let { announcementInfo ->
                    isCloseable = false
                    title = announcementInfo.title
                    subtitle = announcementInfo.message
                    if (announcementInfo.backgroundUrl.isNotEmpty()) {
                        backgroundResource = ImageResource.Remote(announcementInfo.backgroundUrl)
                    }

                    if (announcementInfo.iconUrl.isNotEmpty()) {
                        iconResource = ImageResource.Remote(announcementInfo.iconUrl)
                    }
                } ?: run {
                    // keep old functionality here if no data returned
                    title = getString(R.string.referral_program)
                    subtitle = newState.referralInfo.rewardTitle
                    backgroundResource = ImageResource.Local(R.drawable.bkgd_button_blue)
                }
            }
        }

        if (newState.featureFlagsSet.dustBalancesFF) {
            with(binding) {
                with(generalGroup) {
                    primaryText = getString(R.string.common_general)
                    secondaryText = getString(R.string.settings_general_description)
                    onClick = {
                        navigator().goToGeneralSettings()
                    }
                    visible()
                }
                dividerGeneral.visible()
            }
        }

        if (newState.error != SettingsError.None) {
            renderError(newState.error)
        }
    }

    private fun renderView(newState: SettingsState) {
        when (newState.viewToLaunch) {
            ViewToLaunch.Profile ->
                newState.basicProfileInfo?.let {
                    navigator().goToProfile()
                }
            is ViewToLaunch.BankTransfer -> {
                onBankTransferAddedResult.launch(
                    BankAuthActivity.newInstance(
                        newState.viewToLaunch.linkBankTransfer,
                        BankAuthSource.SETTINGS,
                        requireContext()
                    )
                )
            }
            ViewToLaunch.None -> {
                // do nothing
            }
        }
        model.process(SettingsIntent.ResetViewState)
    }

    private fun renderError(errorState: SettingsError) {
        when (errorState) {
            SettingsError.PaymentMethodsLoadFail -> {
                // TODO error state here? maybe show retry - check with design
            }
            SettingsError.BankLinkStartFail -> {
                BlockchainSnackbar.make(
                    binding.root,
                    getString(R.string.failed_to_link_bank),
                    type = SnackbarType.Error
                ).show()
            }
            is SettingsError.BankLinkMaxAccountsReached -> {
                showBottomSheet(
                    ErrorSlidingBottomDialog.newInstance(
                        ErrorDialogData(
                            title = getString(R.string.bank_linking_max_accounts_title),
                            description = getString(R.string.bank_linking_max_accounts_subtitle),
                            error = errorState.toString(),
                            nabuApiException = errorState.error,
                            errorButtonCopies = ErrorButtonCopies(
                                primaryButtonText = getString(R.string.common_ok)
                            ),
                            analyticsCategories = emptyList()
                        )
                    )
                )
            }
            is SettingsError.BankLinkMaxAttemptsReached -> {
                showBottomSheet(
                    ErrorSlidingBottomDialog.newInstance(
                        ErrorDialogData(
                            title = getString(R.string.bank_linking_max_attempts_title),
                            description = getString(R.string.bank_linking_max_attempts_subtitle),
                            error = errorState.toString(),
                            nabuApiException = errorState.error,
                            errorButtonCopies = ErrorButtonCopies(
                                primaryButtonText = getString(R.string.common_ok)
                            ),
                            analyticsCategories = emptyList()
                        )
                    )
                )
            }
            SettingsError.UnpairFailed -> {
                BlockchainSnackbar.make(
                    binding.root,
                    getString(R.string.settings_logout_error),
                    type = SnackbarType.Error
                ).show()
            }
            SettingsError.None -> {
                // do nothing
            }
        }
        model.process(SettingsIntent.ResetErrorState)
    }

    private fun addPaymentMethods(
        paymentMethodInfo: PaymentMethods,
        totalLinkedPaymentMethods: Int,
        isUserGold: Boolean,
        canPayWithBind: Boolean
    ) {
        val availablePaymentMethodTypes = paymentMethodInfo.availablePaymentMethodTypes
        val linkAccessMap = availablePaymentMethodTypes.associate { it.type to it.linkAccess }

        val hidePaymentsSection = totalLinkedPaymentMethods == 0 &&
            availablePaymentMethodTypes.none { it.linkAccess == LinkAccess.GRANTED }
        binding.headerPayments.goneIf(hidePaymentsSection)
        binding.paymentsContainer.goneIf(hidePaymentsSection)
        if (hidePaymentsSection) return

        when {
            availablePaymentMethodTypes.isNotEmpty() -> {
                with(binding.paymentsContainer) {
                    if (totalLinkedPaymentMethods > 0) {
                        addBanks(paymentMethodInfo)
                        addCards(paymentMethodInfo)
                        val canLinkNewMethods = availablePaymentMethodTypes.any { it.linkAccess == LinkAccess.GRANTED }
                        if (canLinkNewMethods) {
                            addView(
                                MinimalButtonView(requireContext()).apply {
                                    text = getString(R.string.add_payment_method)
                                    onClick = {
                                        if (canPayWithBind) {
                                            bankAliasLinkLauncher.launch(
                                                currencyPrefs.selectedFiatCurrency.networkTicker
                                            )
                                        } else {
                                            showPaymentMethodsBottomSheet(
                                                canAddCard =
                                                linkAccessMap[PaymentMethodType.PAYMENT_CARD] == LinkAccess.GRANTED,
                                                canLinkBank =
                                                linkAccessMap[PaymentMethodType.BANK_TRANSFER] == LinkAccess.GRANTED,
                                            )
                                        }
                                    }
                                },
                                LinearLayoutCompat.LayoutParams(
                                    MATCH_PARENT,
                                    WRAP_CONTENT,
                                ).apply {
                                    marginStart = resources.getDimensionPixelOffset(R.dimen.standard_spacing)
                                    marginEnd = resources.getDimensionPixelOffset(R.dimen.standard_spacing)
                                }
                            )
                        }
                    } else {
                        addView(
                            DefaultTableRowView(requireContext()).apply {
                                primaryText = getString(R.string.settings_title_no_payments)
                                secondaryText = if (canPayWithBind) {
                                    getString(R.string.add_a_bank_account)
                                } else {
                                    getString(R.string.settings_subtitle_no_payments)
                                }
                                onClick = {
                                    if (canPayWithBind) {
                                        bankAliasLinkLauncher.launch(currencyPrefs.selectedFiatCurrency.networkTicker)
                                    } else {
                                        showPaymentMethodsBottomSheet(
                                            canAddCard =
                                            linkAccessMap[PaymentMethodType.PAYMENT_CARD] == LinkAccess.GRANTED,
                                            canLinkBank =
                                            linkAccessMap[PaymentMethodType.BANK_TRANSFER] == LinkAccess.GRANTED,
                                        )
                                    }
                                }
                                startImageResource = ImageResource.Local(R.drawable.ic_payment_card, null)
                            }
                        )
                    }
                }
            }
            else -> {
                if (totalLinkedPaymentMethods > 0) {
                    with(binding.paymentsContainer) {
                        addBanks(paymentMethodInfo)
                        addCards(paymentMethodInfo)
                    }
                } else {
                    with(binding) {
                        if (isUserGold) {
                            paymentsContainer.gone()
                            headerPayments.gone()
                        } else {
                            // TODO show KYC for silver -> gold UI - missing design
                        }
                    }
                }
            }
        }
    }

    private fun LinearLayoutCompat.addCards(paymentMethodInfo: PaymentMethods) {
        paymentMethodInfo.linkedCards.forEach { card ->
            addView(
                BalanceTableRowView(requireContext()).apply {
                    alpha = 0f
                    titleStart = buildAnnotatedString { append(card.uiLabel()) }
                    titleEnd = buildAnnotatedString { append(card.dottedEndDigits()) }
                    startImageResource = ImageResource.Local(
                        (card as? PaymentMethod.Card)?.cardType?.icon()
                            ?: R.drawable.ic_card_icon,
                        null
                    )
                    bodyStart = buildAnnotatedString {
                        append(
                            getString(
                                R.string.common_spaced_strings, card.limits.max.toStringWithSymbol(),
                                getString(R.string.deposit_enter_amount_limit_title)
                            )
                        )
                    }
                    bodyEnd = buildAnnotatedString {
                        append(
                            getString(R.string.card_expiry_date, card.expireDate.formatted())
                        )
                    }
                    onClick = {
                        showBottomSheet(RemoveCardBottomSheet.newInstance(card))
                    }

                    tags = when (val cardState = card.cardRejectionState) {
                        is CardRejectionState.AlwaysRejected -> {
                            listOf(
                                TagViewState(
                                    cardState.title ?: getString(R.string.card_issuer_always_rejects_title),
                                    TagType.Error()
                                )
                            )
                        }
                        is CardRejectionState.MaybeRejected -> {
                            listOf(
                                TagViewState(
                                    cardState.title ?: getString(R.string.card_issuer_sometimes_rejects_title),
                                    TagType.Warning()
                                )
                            )
                        }
                        else -> null
                    }

                    animate().alpha(1f)
                }
            )
        }
    }

    private fun LinearLayoutCompat.addBanks(paymentMethodInfo: PaymentMethods) {
        paymentMethodInfo.linkedBanks.forEach { bankItem ->
            val bank = bankItem.bank
            addView(
                BalanceTableRowView(requireContext()).apply {
                    alpha = 0f
                    titleStart = buildAnnotatedString { append(bank.name) }
                    titleEnd =
                        buildAnnotatedString { append(getString(R.string.dotted_suffixed_string, bank.accountEnding)) }
                    startImageResource = if (bank.iconUrl.isEmpty()) {
                        ImageResource.Local(R.drawable.ic_bank_icon, null)
                    } else {
                        ImageResource.Remote(url = bank.iconUrl, null)
                    }
                    bodyStart = buildAnnotatedString {
                        append(
                            getString(
                                R.string.common_spaced_strings, bankItem.limits.max.toStringWithSymbol(),
                                getString(R.string.deposit_enter_amount_limit_title)
                            )
                        )
                    }
                    bodyEnd = buildAnnotatedString {
                        append(bank.accountType)
                    }

                    if (!bankItem.canBeUsedToTransact) {
                        tags = listOf(
                            TagViewState(
                                getString(R.string.common_unavailable), TagType.Error()
                            )
                        )
                    }
                    onClick = {
                        showBottomSheet(RemoveLinkedBankBottomSheet.newInstance(bank))
                    }
                    animate().alpha(1f)
                }
            )
        }
    }

    private fun showUserTierIcon(tier: KycTier) {
        binding.iconUser.setImageResource(
            when (tier) {
                KycTier.GOLD -> R.drawable.bkgd_profile_icon_gold
                KycTier.SILVER -> R.drawable.bkgd_profile_icon_silver
                else -> 0
            }
        )
    }

    private fun setupMenuItems(basicProfileInfo: BasicProfileInfo?) {
        with(binding) {
            seeProfile.apply {
                text = context.getString(R.string.settings_see_profile)
                onClick = {
                    basicProfileInfo?.let {
                        navigator().goToProfile()
                    }
                }
            }

            headerPayments.title = getString(R.string.settings_label_payments)
            headerSettings.title = getString(R.string.settings_label_settings)

            accountGroup.apply {
                primaryText = getString(R.string.settings_title_account)
                secondaryText = getString(R.string.settings_subtitle_account)
                onClick = {
                    navigator().goToAccount()
                }
            }

            notificationsGroup.apply {
                primaryText = getString(R.string.settings_notifications_title)
                secondaryText = getString(R.string.settings_notifications_subtitle)
                onClick = {
                    navigator().goToNotifications()
                }
            }

            securityGroup.apply {
                primaryText = getString(R.string.settings_title_security)
                secondaryText = getString(R.string.settings_subtitle_security)
                onClick = {
                    navigator().goToSecurity()
                }
            }

            aboutAppGroup.apply {
                primaryText = getString(R.string.settings_title_about_app)
                secondaryText = getString(R.string.settings_subtitle_about_app)
                onClick = {
                    navigator().goToAboutApp()
                }
            }

            signOutBtn.apply {
                text = getString(R.string.settings_sign_out)
                onClick = { showLogoutDialog() }
            }

            settingsDebug.apply {
                visibleIf { environmentConfig.isRunningInDebugMode() }
                primaryText = getString(R.string.item_debug_menu)
                onClick = {
                    navigator().goToFeatureFlags()
                }
                startImageResource = ImageResource.Local(R.drawable.ic_nav_debug_swap, null)
            }
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext(), R.style.AlertDialogStyle)
            .setTitle(R.string.settings_signout_wallet)
            .setMessage(R.string.settings_ask_you_sure_signout)
            .setPositiveButton(R.string.settings_btn_signout) { _, _ -> model.process(SettingsIntent.Logout) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPaymentMethodsBottomSheet(
        canAddCard: Boolean,
        canLinkBank: Boolean
    ) {
        showBottomSheet(
            AddPaymentMethodsBottomSheet.newInstance(
                canAddCard = canAddCard,
                canLinkBank = canLinkBank
            )
        )
    }

    override fun onAddCardSelected() {
        analytics.logEvent(SimpleBuyAnalytics.SETTINGS_ADD_CARD)
        onCardAddedResult.launch(CardDetailsActivity.newIntent(requireContext()))
    }

    override fun onLinkBankSelected() {
        model.process(SettingsIntent.AddLinkBankSelected)
    }

    override fun onCardRemoved(cardId: String) {
        model.process(SettingsIntent.OnCardRemoved(cardId))
    }

    override fun onLinkedBankRemoved(bankId: String) {
        model.process(SettingsIntent.OnBankRemoved(bankId))
    }

    override fun onErrorPrimaryCta() {
        // do nothing
    }

    override fun onErrorSecondaryCta() {
        // do nothing
    }

    override fun onErrorTertiaryCta() {
        // do nothing
    }

    override fun onSheetClosed() {
        // do nothing
    }

    private fun setInfoHeader(userInformation: BasicProfileInfo, tier: KycTier) {
        if (tier == KycTier.BRONZE) {
            setUserTier0Info(userInformation.email)
        } else {
            setUserInfo(userInformation)
        }
    }

    private fun setUserInfo(userInformation: BasicProfileInfo) {
        with(binding) {
            name.text = getString(
                R.string.common_spaced_strings, userInformation.firstName, userInformation.lastName
            )
            name.animate().alpha(1f)
            email.apply {
                text = userInformation.email
                style = ComposeTypographies.Body1
                textColor = ComposeColors.Body
                this.animate().alpha(1f)
                gravity = ComposeGravities.Centre
            }
            userInitials.background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bkgd_profile_circle
            )
            userInitials.text = getString(
                R.string.settings_initials,
                userInformation.firstName.first().uppercase(),
                userInformation.lastName.first().uppercase()
            )
            seeProfile.visible()
            iconUser.animate().alpha(1f)
        }
    }

    private fun setUserTier0Info(emailAddress: String) {
        with(binding) {
            name.text = emailAddress
            userInitials.background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bkgd_profile_circle_empty
            )
            name.animate().alpha(1f)
            email.gone()
            seeProfile.visible()
        }
    }

    // Network calls fail or lack of connectivity UI
    private fun setupEmptyHeader() {
        with(binding) {
            userInitials.background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.bkgd_profile_circle_empty
            )
            name.alpha = 0f
            email.alpha = 0f
            seeProfile.gone()
        }
    }

    private fun Date.formatted() =
        SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(this)

    companion object {
        private const val LOTTIE_LOADER_PATH = "lottie/loader.json"

        fun newInstance(): SettingsFragment = SettingsFragment()
    }
}
