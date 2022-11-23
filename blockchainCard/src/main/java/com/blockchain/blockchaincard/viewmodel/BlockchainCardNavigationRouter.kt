package com.blockchain.blockchaincard.viewmodel

import androidx.navigation.NavHostController
import com.blockchain.blockchaincard.domain.models.BlockchainCard
import com.blockchain.blockchaincard.domain.models.BlockchainCardAddress
import com.blockchain.blockchaincard.domain.models.BlockchainCardGoogleWalletPushTokenizeData
import com.blockchain.blockchaincard.domain.models.BlockchainCardProduct
import com.blockchain.blockchaincard.ui.BlockchainCardHostActivity
import com.blockchain.coincore.FiatAccount
import com.blockchain.commonarch.presentation.mvi_v2.NavigationEvent
import com.blockchain.commonarch.presentation.mvi_v2.compose.ComposeNavigationDestination
import com.blockchain.commonarch.presentation.mvi_v2.compose.ComposeNavigationRouter
import com.blockchain.extensions.exhaustive
import info.blockchain.balance.AssetInfo

class BlockchainCardNavigationRouter(override val navController: NavHostController) :
    ComposeNavigationRouter<BlockchainCardNavigationEvent> {

    override fun route(navigationEvent: BlockchainCardNavigationEvent) {
        var destination: BlockchainCardDestination = BlockchainCardDestination.NoDestination

        @Suppress("IMPLICIT_CAST_TO_ANY")
        when (navigationEvent) {

            is BlockchainCardNavigationEvent.ShowOrderCardIntro -> {
                navController.popBackStack(BlockchainCardDestination.LoadingKycStatusDestination.route, true)
                destination = BlockchainCardDestination.OrderCardIntroDestination
            }

            is BlockchainCardNavigationEvent.ShowHowToOrderCard -> {
                destination = BlockchainCardDestination.HowToOrderCardDestination
            }

            is BlockchainCardNavigationEvent.OrderCardKycPending -> {
                navController.popBackStack(BlockchainCardDestination.LoadingKycStatusDestination.route, true)
                destination = BlockchainCardDestination.OrderCardKycPendingDestination
            }

            is BlockchainCardNavigationEvent.OrderCardKycFailure -> {
                navController.popBackStack(BlockchainCardDestination.LoadingKycStatusDestination.route, true)
                destination = BlockchainCardDestination.OrderCardKycFailureDestination
            }

            is BlockchainCardNavigationEvent.OrderCardKycAddress -> {
                destination = BlockchainCardDestination.OrderCardKycAddressDestination
            }

            is BlockchainCardNavigationEvent.OrderCardKycSSN -> {
                destination = BlockchainCardDestination.OrderCardKycSSNDestination
            }

            is BlockchainCardNavigationEvent.OrderCardKycPendingComplete -> {
                (navController.context as? BlockchainCardHostActivity)?.finishOrderCardFlow()
            }

            is BlockchainCardNavigationEvent.ChooseCardProduct -> {
                val wasPopped = navController.popBackStack(
                    BlockchainCardDestination.ChooseCardProductDestination.route,
                    false
                )
                if (wasPopped) {
                    destination = BlockchainCardDestination.NoDestination
                } else {
                    destination = BlockchainCardDestination.ChooseCardProductDestination
                }
            }

            is BlockchainCardNavigationEvent.ReviewAndSubmitCard -> {
                // Check if this destination is already in the backstack (popBackStack returns true)
                // If not, create it and navigate to it
                // If yes pop to it and return null.
                if (!navController.popBackStack(BlockchainCardDestination.ReviewAndSubmitCardDestination.route, false))
                    destination = BlockchainCardDestination.ReviewAndSubmitCardDestination
                else null
            }

            is BlockchainCardNavigationEvent.RetryOrderCard -> {
                navController.popBackStack(BlockchainCardDestination.OrderCardIntroDestination.route, false)
            }

            is BlockchainCardNavigationEvent.SeeProductDetails -> {
                destination = BlockchainCardDestination.SeeProductDetailsDestination
            }

            is BlockchainCardNavigationEvent.SeeProductLegalInfo -> {
                destination = BlockchainCardDestination.SeeProductLegalInfoDestination
            }

            is BlockchainCardNavigationEvent.HideBottomSheet -> {
                navController.popBackStack()
            }

            is BlockchainCardNavigationEvent.CreateCardInProgress -> {
                destination = BlockchainCardDestination.CreateCardInProgressDestination
            }

            is BlockchainCardNavigationEvent.CreateCardSuccess -> {
                navController.popBackStack(BlockchainCardDestination.OrderCardIntroDestination.route, true)
                destination = BlockchainCardDestination.CreateCardSuccessDestination
            }

            is BlockchainCardNavigationEvent.CreateCardFailed -> {
                navController.popBackStack(BlockchainCardDestination.OrderCardIntroDestination.route, false)
                destination = BlockchainCardDestination.CreateCardFailedDestination
            }

            is BlockchainCardNavigationEvent.FinishOrderCardFlow -> {
                (navController.context as? BlockchainCardHostActivity)?.orderCardFlowComplete(
                    blockchainCard = navigationEvent.createdCard
                )
            }

            is BlockchainCardNavigationEvent.ManageCard -> {
                navController.popBackStack(
                    route = BlockchainCardDestination.ManageCardDestination.route,
                    inclusive = true
                )
                destination = BlockchainCardDestination.ManageCardDestination
            }

            is BlockchainCardNavigationEvent.ViewCardSelector -> {

                val wasPopped = navController.popBackStack(
                    route = BlockchainCardDestination.SelectCardDestination.route,
                    inclusive = false
                )

                if (!wasPopped) destination = BlockchainCardDestination.SelectCardDestination
                else destination = BlockchainCardDestination.NoDestination
            }

            is BlockchainCardNavigationEvent.OrderCard -> {
                (navController.context as? BlockchainCardHostActivity)?.startOrderCardFlow(
                    isInitialFlow = false,
                    products = navigationEvent.products
                )
            }

            is BlockchainCardNavigationEvent.ManageCardDetails -> {
                destination = BlockchainCardDestination.ManageCardDetailsDestination
            }

            is BlockchainCardNavigationEvent.ChoosePaymentMethod -> {
                destination = BlockchainCardDestination.ChoosePaymentMethodDestination
            }

            is BlockchainCardNavigationEvent.CardClosed -> {
                val wasPopped = navController.popBackStack(
                    route = BlockchainCardDestination.SelectCardDestination.route,
                    inclusive = false
                )

                if (!wasPopped) destination = BlockchainCardDestination.SelectCardDestination
                else destination = BlockchainCardDestination.NoDestination
            }

            is BlockchainCardNavigationEvent.ChooseFundingAccountAction -> {
                destination = BlockchainCardDestination.FundingAccountActionsDestination
            }

            is BlockchainCardNavigationEvent.TopUpCrypto -> {
                (navController.context as? BlockchainCardHostActivity)?.startBuy(navigationEvent.asset)
            }

            is BlockchainCardNavigationEvent.TopUpFiat -> {
                (navController.context as? BlockchainCardHostActivity)?.startDeposit(navigationEvent.account)
            }

            is BlockchainCardNavigationEvent.SeeTransactionControls -> {
                destination = BlockchainCardDestination.TransactionControlsDestination
            }

            is BlockchainCardNavigationEvent.SeePersonalDetails -> {
                destination = BlockchainCardDestination.PersonalDetailsDestination
            }

            is BlockchainCardNavigationEvent.SeeAddress -> {
                (navController.context as? BlockchainCardHostActivity)?.startKycAddressVerification(
                    address = navigationEvent.address
                )
            }

            is BlockchainCardNavigationEvent.SeeSupport -> {
                destination = BlockchainCardDestination.SupportDestination
            }

            is BlockchainCardNavigationEvent.CloseCard -> {
                destination = BlockchainCardDestination.CloseCardDestination
            }

            is BlockchainCardNavigationEvent.BillingAddressUpdated -> {
                navController.popBackStack(BlockchainCardDestination.BillingAddressDestination.route, true)
                if (navigationEvent.success)
                    destination = BlockchainCardDestination.BillingAddressUpdateSuccessDestination
                else
                    destination = BlockchainCardDestination.BillingAddressUpdateFailedDestination
            }

            is BlockchainCardNavigationEvent.DismissBillingAddressUpdateResult -> {
                navController.popBackStack()
            }

            is BlockchainCardNavigationEvent.SeeAllTransactions -> {
                destination = BlockchainCardDestination.AllTransactionsDestination
            }

            is BlockchainCardNavigationEvent.SeeTransactionDetails -> {
                destination = BlockchainCardDestination.TransactionDetailsDestination
            }

            is BlockchainCardNavigationEvent.SeeSingleLegalDocument -> {
                destination = BlockchainCardDestination.SingleLegalDocumentDestination
            }

            is BlockchainCardNavigationEvent.SeeLegalDocuments -> {
                destination = BlockchainCardDestination.LegalDocumentsDestination
            }

            is BlockchainCardNavigationEvent.FinishLegalDocReview -> {
                navController.popBackStack(
                    route = BlockchainCardDestination.ReviewAndSubmitCardDestination.route,
                    inclusive = false
                )
            }

            is BlockchainCardNavigationEvent.SeeCardLostPage -> {
                destination = BlockchainCardDestination.CardLostPageDestination
            }

            is BlockchainCardNavigationEvent.SeeFAQPage -> {
                destination = BlockchainCardDestination.FAQPageDestination
            }

            is BlockchainCardNavigationEvent.SeeContactSupportPage -> {
                destination = BlockchainCardDestination.ContactSupportPageDestination
            }

            is BlockchainCardNavigationEvent.AddCardToGoogleWallet -> {
                (navController.context as BlockchainCardHostActivity).startAddCardToGoogleWallet(
                    pushTokenizeData = navigationEvent.blockchainCardTokenizationRequest
                )
            }

            is BlockchainCardNavigationEvent.SeeCardActivationPage -> {
                destination = BlockchainCardDestination.CardActivationDestination
            }

            is BlockchainCardNavigationEvent.ActivateCardSuccess -> {
                navController.popBackStack(
                    route = BlockchainCardDestination.CardActivationDestination.route,
                    inclusive = true
                )
                destination = BlockchainCardDestination.CardActivationSuccessDestination
            }

            is BlockchainCardNavigationEvent.SeeDocuments -> {
                destination = BlockchainCardDestination.DocumentsDestination
            }

            is BlockchainCardNavigationEvent.OpenDocumentUrl -> {
                (navController.context as? BlockchainCardHostActivity)?.openUrl(navigationEvent.url)
            }
        }.exhaustive

        if (destination !is BlockchainCardDestination.NoDestination)
            navController.navigate(destination.route)
    }
}

sealed class BlockchainCardNavigationEvent : NavigationEvent {

    // Order Card

    object ShowOrderCardIntro : BlockchainCardNavigationEvent()

    object ShowHowToOrderCard : BlockchainCardNavigationEvent()

    object OrderCardKycPending : BlockchainCardNavigationEvent()

    object OrderCardKycFailure : BlockchainCardNavigationEvent()

    object OrderCardKycAddress : BlockchainCardNavigationEvent()

    object OrderCardKycSSN : BlockchainCardNavigationEvent()

    object OrderCardKycPendingComplete : BlockchainCardNavigationEvent()

    object ChooseCardProduct : BlockchainCardNavigationEvent()

    object ReviewAndSubmitCard : BlockchainCardNavigationEvent()

    object RetryOrderCard : BlockchainCardNavigationEvent()

    object CreateCardInProgress : BlockchainCardNavigationEvent()

    object CreateCardSuccess : BlockchainCardNavigationEvent()

    object CreateCardFailed : BlockchainCardNavigationEvent()

    object HideBottomSheet : BlockchainCardNavigationEvent()

    object SeeProductDetails : BlockchainCardNavigationEvent()

    object SeeProductLegalInfo : BlockchainCardNavigationEvent()

    object FinishLegalDocReview : BlockchainCardNavigationEvent()

    data class FinishOrderCardFlow(val createdCard: BlockchainCard) : BlockchainCardNavigationEvent()

    // Manage Card

    data class ViewCardSelector(val hasDefault: Boolean) : BlockchainCardNavigationEvent()

    data class OrderCard(val products: List<BlockchainCardProduct>) : BlockchainCardNavigationEvent()

    object ManageCard : BlockchainCardNavigationEvent()

    object ManageCardDetails : BlockchainCardNavigationEvent()

    object ChoosePaymentMethod : BlockchainCardNavigationEvent()

    object ChooseFundingAccountAction : BlockchainCardNavigationEvent()

    data class TopUpCrypto(val asset: AssetInfo) : BlockchainCardNavigationEvent()

    data class TopUpFiat(val account: FiatAccount) : BlockchainCardNavigationEvent()

    object SeeTransactionControls : BlockchainCardNavigationEvent()

    object SeePersonalDetails : BlockchainCardNavigationEvent()

    data class SeeAddress(val address: BlockchainCardAddress) : BlockchainCardNavigationEvent()

    object SeeSupport : BlockchainCardNavigationEvent()

    object CloseCard : BlockchainCardNavigationEvent()

    object CardClosed : BlockchainCardNavigationEvent()

    data class BillingAddressUpdated(val success: Boolean) : BlockchainCardNavigationEvent()

    object DismissBillingAddressUpdateResult : BlockchainCardNavigationEvent()

    object SeeAllTransactions : BlockchainCardNavigationEvent()

    object SeeTransactionDetails : BlockchainCardNavigationEvent()

    object SeeSingleLegalDocument : BlockchainCardNavigationEvent()

    object SeeLegalDocuments : BlockchainCardNavigationEvent()

    object SeeCardLostPage : BlockchainCardNavigationEvent()

    object SeeFAQPage : BlockchainCardNavigationEvent()

    object SeeContactSupportPage : BlockchainCardNavigationEvent()

    data class AddCardToGoogleWallet(
        val blockchainCardTokenizationRequest: BlockchainCardGoogleWalletPushTokenizeData
    ) : BlockchainCardNavigationEvent()

    object SeeCardActivationPage : BlockchainCardNavigationEvent()

    object ActivateCardSuccess : BlockchainCardNavigationEvent()

    object SeeDocuments : BlockchainCardNavigationEvent()

    data class OpenDocumentUrl(val url: String) : BlockchainCardNavigationEvent()
}

sealed class BlockchainCardDestination(override val route: String) : ComposeNavigationDestination {

    object NoDestination : BlockchainCardDestination(route = "")

    object LoadingKycStatusDestination : BlockchainCardDestination(route = "loading_kyc_status")

    object OrderCardIntroDestination : BlockchainCardDestination(route = "order_card_intro")

    object HowToOrderCardDestination : BlockchainCardDestination(route = "how_to_order_card")

    object OrderCardKycPendingDestination : BlockchainCardDestination(route = "order_card_kyc_pending")

    object OrderCardKycFailureDestination : BlockchainCardDestination(route = "order_card_kyc_failure")

    object OrderCardKycAddressDestination : BlockchainCardDestination(route = "order_card_kyc_address")

    object OrderCardKycSSNDestination : BlockchainCardDestination(route = "order_card_kyc_ssn")

    object ChooseCardProductDestination : BlockchainCardDestination(route = "choose_card_product")

    object ReviewAndSubmitCardDestination : BlockchainCardDestination(route = "review_and_submit")

    object CreateCardInProgressDestination : BlockchainCardDestination(route = "create_card_in_progress")

    object CreateCardSuccessDestination : BlockchainCardDestination(route = "create_card_success")

    object CreateCardFailedDestination : BlockchainCardDestination(route = "create_card_failed")

    // TODO(labreu): these should just be named "ProductDetailsDestination" and "ProductLegalInfoDestination"
    object SeeProductDetailsDestination : BlockchainCardDestination(route = "product_details")

    object SeeProductLegalInfoDestination : BlockchainCardDestination(route = "product_legal_info")

    object SelectCardDestination : BlockchainCardDestination(route = "select_card")

    object ManageCardDestination : BlockchainCardDestination(route = "manage_card")

    object ManageCardDetailsDestination : BlockchainCardDestination(route = "manage_card_details")

    object FundingAccountActionsDestination : BlockchainCardDestination(route = "funding_account_actions")

    object ChoosePaymentMethodDestination : BlockchainCardDestination(route = "choose_payment_method")

    object TransactionControlsDestination : BlockchainCardDestination(route = "transaction_controls")

    object PersonalDetailsDestination : BlockchainCardDestination(route = "personal_details")

    object BillingAddressDestination : BlockchainCardDestination(route = "billing_address")

    object SupportDestination : BlockchainCardDestination(route = "support")

    object CloseCardDestination : BlockchainCardDestination(route = "close_card")

    object BillingAddressUpdateSuccessDestination :
        BlockchainCardDestination(route = "billing_address_update_success")

    object BillingAddressUpdateFailedDestination :
        BlockchainCardDestination(route = "billing_address_update_failed")

    object AllTransactionsDestination : BlockchainCardDestination(route = "all_transactions")

    object TransactionDetailsDestination : BlockchainCardDestination(route = "transaction_details")

    object SingleLegalDocumentDestination : BlockchainCardDestination(route = "single_legal_document")

    object LegalDocumentsDestination : BlockchainCardDestination(route = "legal_documents")

    object CardLostPageDestination : BlockchainCardDestination(route = "card_lost_page")

    object FAQPageDestination : BlockchainCardDestination(route = "faq_page")

    object ContactSupportPageDestination : BlockchainCardDestination(route = "contact_support_page")

    object CardActivationDestination : BlockchainCardDestination(route = "card_activation")

    object CardActivationSuccessDestination : BlockchainCardDestination(route = "card_activation_success")

    object DocumentsDestination : BlockchainCardDestination(route = "documents")
}
