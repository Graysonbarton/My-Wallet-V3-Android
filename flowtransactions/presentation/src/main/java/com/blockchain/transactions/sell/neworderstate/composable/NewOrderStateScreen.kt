package com.blockchain.transactions.sell.neworderstate.composable

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blockchain.analytics.Analytics
import com.blockchain.api.NabuApiException
import com.blockchain.api.isInternetConnectionError
import com.blockchain.betternavigation.NavContext
import com.blockchain.betternavigation.navigateTo
import com.blockchain.componentlib.basic.ComposeColors
import com.blockchain.componentlib.basic.ComposeGravities
import com.blockchain.componentlib.basic.ComposeTypographies
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.basic.SimpleText
import com.blockchain.componentlib.button.PrimaryButton
import com.blockchain.componentlib.button.SecondaryButton
import com.blockchain.componentlib.button.TertiaryButton
import com.blockchain.componentlib.icon.SmallTagIcon
import com.blockchain.componentlib.icons.Alert
import com.blockchain.componentlib.icons.Check
import com.blockchain.componentlib.icons.Icons
import com.blockchain.componentlib.icons.Pending
import com.blockchain.componentlib.tablerow.custom.StackedIcon
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.componentlib.theme.SmallVerticalSpacer
import com.blockchain.componentlib.theme.TinyHorizontalSpacer
import com.blockchain.core.buy.domain.SimpleBuyService
import com.blockchain.deeplinking.navigation.DeeplinkRedirector
import com.blockchain.deeplinking.processor.DeepLinkResult
import com.blockchain.domain.common.model.ServerErrorAction
import com.blockchain.domain.common.model.ServerSideUxErrorInfo
import com.blockchain.koin.payloadScope
import com.blockchain.outcome.doOnSuccess
import com.blockchain.presentation.checkValidUrlAndOpen
import com.blockchain.transactions.presentation.R
import com.blockchain.transactions.sell.SellAnalyticsEvents
import com.blockchain.transactions.sell.SellGraph
import com.blockchain.utils.awaitOutcome
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatCurrency
import info.blockchain.balance.FiatValue
import info.blockchain.balance.isLayer2Token
import java.io.Serializable
import org.koin.androidx.compose.get

sealed interface NewOrderState {
    object PendingDeposit : NewOrderState
    object Succeeded : NewOrderState
    data class Error(val error: Exception) : NewOrderState
}

data class NewOrderStateArgs(
    val sourceAmount: CryptoValue,
    val targetAmount: FiatValue,
    val orderState: NewOrderState
) : Serializable

@Composable
fun NavContext.NewOrderStateScreen(
    analytics: Analytics = get(),
    args: NewOrderStateArgs,
    deeplinkRedirector: DeeplinkRedirector = get(scope = payloadScope),
    simpleBuyService: SimpleBuyService = get(scope = payloadScope),
    exitFlow: () -> Unit
) {
    val context = LocalContext.current
    var handleDeeplinkUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(handleDeeplinkUrl) {
        val url = handleDeeplinkUrl?.appendTickerToDeeplink(args.sourceAmount.currencyCode)
        handleDeeplinkUrl = null
        if (url != null) {
            deeplinkRedirector.processDeeplinkURL(url).awaitOutcome()
                .doOnSuccess {
                    if (it is DeepLinkResult.DeepLinkResultUnknownLink) {
                        it.uri?.let { uri ->
                            context.checkValidUrlAndOpen(uri)
                        }
                    }
                }
            exitFlow()
        }
    }

    LaunchedEffect(args.orderState) {
        when (args.orderState) {
            NewOrderState.PendingDeposit -> analytics.logEvent(SellAnalyticsEvents.PendingViewed)
            NewOrderState.Succeeded -> analytics.logEvent(SellAnalyticsEvents.SuccessViewed)
            is NewOrderState.Error -> analytics.logEvent(SellAnalyticsEvents.ErrorViewed)
        }
    }

    NewOrderStateContent(
        sourceAmount = args.sourceAmount,
        targetAmount = args.targetAmount,
        orderState = args.orderState,
        handleDeeplinkUrlAndExit = { deeplinkUrl ->
            handleDeeplinkUrl = deeplinkUrl
        },
        doneClicked = {
            if (simpleBuyService.shouldShowUpsellAnotherAsset()) {
                navigateTo(SellGraph.UpsellAnotherAsset, args.sourceAmount.currency.networkTicker)
            } else {
                exitFlow()
            }
        }
    )
}

@Composable
private fun NewOrderStateContent(
    sourceAmount: CryptoValue,
    targetAmount: FiatValue,
    orderState: NewOrderState,
    handleDeeplinkUrlAndExit: (String) -> Unit,
    doneClicked: () -> Unit
) {
    Column(
        modifier = Modifier.background(AppTheme.colors.light)
    ) {
        Spacer(Modifier.weight(0.33f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val assetIcon = ImageResource.Remote(
                url = sourceAmount.currency.logo,
                shape = CircleShape,
                size = 88.dp
            )
            val tagIcon = when (orderState) {
                NewOrderState.PendingDeposit ->
                    Icons.Filled.Pending
                        .withTint(AppTheme.colors.muted)
                        .withSize(44.dp)
                NewOrderState.Succeeded ->
                    Icons.Filled.Check
                        .withTint(AppTheme.colors.success)
                        .withSize(44.dp)
                is NewOrderState.Error ->
                    errorStatusIcon(orderState)
            }

            val stackedIcon = StackedIcon.SmallTag(
                main = assetIcon,
                tag = tagIcon
            )

            SmallTagIcon(
                icon = stackedIcon,
                iconBackground = AppTheme.colors.light,
                mainIconSize = 88.dp,
                tagIconSize = 44.dp,
                borderColor = AppTheme.colors.light
            )

            SmallVerticalSpacer()

            val title = when (orderState) {
                NewOrderState.PendingDeposit ->
                    stringResource(
                        com.blockchain.stringResources.R.string.sell_neworderstate_pending_deposit_title,
                        sourceAmount.currency.name
                    )
                NewOrderState.Succeeded ->
                    stringResource(com.blockchain.stringResources.R.string.sell_neworderstate_succeeded_title)
                is NewOrderState.Error ->
                    errorTitle(orderState)
            }
            SimpleText(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.dimensions.standardSpacing),
                text = title,
                style = ComposeTypographies.Title3,
                color = ComposeColors.Title,
                gravity = ComposeGravities.Centre
            )

            TinyHorizontalSpacer()

            val description = when (orderState) {
                NewOrderState.PendingDeposit -> {
                    val l2CoinNetworkName = if (
                        sourceAmount.currency.isLayer2Token &&
                        sourceAmount.currency.coinNetwork != null
                    ) {
                        sourceAmount.currency.coinNetwork!!.name
                    } else {
                        null
                    }
                    if (l2CoinNetworkName != null) {
                        stringResource(
                            com.blockchain.stringResources.R.string.sell_neworderstate_pending_deposit_l2_description,
                            targetAmount.toStringWithSymbol(),
                            sourceAmount.toStringWithSymbol(),
                            l2CoinNetworkName,
                            // TODO(aromano): usually X minutes
                            "5"
                        )
                    } else {
                        stringResource(
                            com.blockchain.stringResources.R.string.sell_neworderstate_pending_deposit_description,
                            targetAmount.toStringWithSymbol(),
                            sourceAmount.toStringWithSymbol(),
                            // TODO(aromano): usually X minutes
                            "5"
                        )
                    }
                }
                NewOrderState.Succeeded -> stringResource(
                    com.blockchain.stringResources.R.string.sell_neworderstate_succeeded_description,
                    sourceAmount.toStringWithSymbol(),
                    targetAmount.toStringWithSymbol()
                )
                is NewOrderState.Error ->
                    errorDescription(orderState)
            }
            SimpleText(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTheme.dimensions.standardSpacing),
                text = description,
                style = ComposeTypographies.Body1,
                color = ComposeColors.Body,
                gravity = ComposeGravities.Centre
            )
        }

        Spacer(Modifier.weight(0.66f))

        if (orderState is NewOrderState.Error) {
            ErrorCtaButtons(
                state = orderState,
                onCtaClicked = { deeplinkUrl ->
                    handleDeeplinkUrlAndExit(deeplinkUrl)
                }
            )
        } else {
            PrimaryButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppTheme.dimensions.smallSpacing),
                text = stringResource(com.blockchain.stringResources.R.string.common_done),
                onClick = doneClicked
            )
        }
    }
}

@Composable
private fun errorStatusIcon(state: NewOrderState.Error): ImageResource {
    val serverSideError = (state.error as? NabuApiException)?.getServerSideErrorInfo()
    val serverSideIcon = serverSideError?.statusUrl?.ifEmpty { null }

    return if (serverSideIcon != null) {
        ImageResource.Remote(
            url = serverSideIcon,
            shape = CircleShape,
            size = 44.dp
        )
    } else {
        Icons.Filled.Alert
            .withTint(AppTheme.colors.error)
            .withSize(44.dp)
    }
}

@Composable
private fun errorTitle(state: NewOrderState.Error): String {
    val serverSideError = (state.error as? NabuApiException)?.getServerSideErrorInfo()
    val message = when {
        serverSideError != null -> serverSideError.title
        state.error.isInternetConnectionError() -> stringResource(
            com.blockchain.stringResources.R.string.executing_connection_error
        )
        else -> null
    }?.ifEmpty { null }

    return message ?: stringResource(com.blockchain.stringResources.R.string.something_went_wrong_try_again)
}

@Composable
private fun errorDescription(state: NewOrderState.Error): String {
    val serverSideError = (state.error as? NabuApiException)?.getServerSideErrorInfo()
    val message = when {
        serverSideError != null -> serverSideError.description
        state.error is NabuApiException -> state.error.getErrorDescription()
        else -> null
    }?.ifEmpty { null }

    return message ?: stringResource(com.blockchain.stringResources.R.string.order_error_subtitle)
}

@Composable
private fun ErrorCtaButtons(
    state: NewOrderState.Error,
    onCtaClicked: (deeplinkUrl: String) -> Unit
) {
    val serverSideError = (state.error as? NabuApiException)?.getServerSideErrorInfo()
    val actions = serverSideError?.actions.orEmpty()
    val modifier = Modifier
        .fillMaxWidth()
        .padding(AppTheme.dimensions.smallSpacing)

    if (actions.isNotEmpty()) {
        actions.forEachIndexed { index, action ->
            val title = action.title.ifEmpty { stringResource(com.blockchain.stringResources.R.string.common_ok) }
            val onClick = { onCtaClicked(action.deeplinkPath) }
            when (index) {
                0 -> PrimaryButton(modifier = modifier, text = title, onClick = onClick)
                1 -> SecondaryButton(modifier = modifier, text = title, onClick = onClick)
                2 -> TertiaryButton(modifier = modifier, text = title, onClick = onClick)
            }
        }
    } else {
        PrimaryButton(
            modifier = modifier,
            text = stringResource(com.blockchain.stringResources.R.string.common_ok),
            onClick = { onCtaClicked("") }
        )
    }
}

private fun String.appendTickerToDeeplink(currencyCode: String): Uri =
    Uri.parse("$this?code=$currencyCode")

@Preview
@Composable
private fun PreviewPendingDeposit() {
    NewOrderStateContent(
        sourceAmount = CryptoValue.fromMajor(CryptoCurrency.ETHER, 0.5.toBigDecimal()),
        targetAmount = FiatValue.fromMajor(FiatCurrency.Dollars, 50.0.toBigDecimal()),
        orderState = NewOrderState.PendingDeposit,
        handleDeeplinkUrlAndExit = {},
        doneClicked = {}
    )
}

@Preview
@Composable
private fun PreviewSucceeded() {
    NewOrderStateContent(
        sourceAmount = CryptoValue.fromMajor(CryptoCurrency.ETHER, 0.5.toBigDecimal()),
        targetAmount = FiatValue.fromMajor(FiatCurrency.Dollars, 50.0.toBigDecimal()),
        orderState = NewOrderState.Succeeded,
        handleDeeplinkUrlAndExit = {},
        doneClicked = {}
    )
}

@Preview
@Composable
private fun PreviewError() {
    val error = ServerSideUxErrorInfo(
        id = null,
        title = "Error title",
        description = "Error description",
        iconUrl = "",
        statusUrl = "",
        actions = listOf(
            ServerErrorAction("One", ""),
            ServerErrorAction("Two", "")
        ),
        categories = emptyList()
    )
    val apiException = NabuApiException(
        message = "some error",
        httpErrorCode = 400,
        errorType = null,
        errorCode = null,
        errorDescription = "nabu error description",
        path = null,
        id = null,
        serverSideUxError = error
    )
    NewOrderStateContent(
        sourceAmount = CryptoValue.fromMajor(CryptoCurrency.ETHER, 0.5.toBigDecimal()),
        targetAmount = FiatValue.fromMajor(FiatCurrency.Dollars, 50.0.toBigDecimal()),
        orderState = NewOrderState.Error(apiException),
        handleDeeplinkUrlAndExit = {},
        doneClicked = {}
    )
}
