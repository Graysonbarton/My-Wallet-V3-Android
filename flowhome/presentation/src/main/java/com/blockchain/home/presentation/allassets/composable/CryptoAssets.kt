package com.blockchain.home.presentation.allassets.composable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.control.CancelableOutlinedSearch
import com.blockchain.componentlib.icon.CustomStackedIcon
import com.blockchain.componentlib.navigation.NavigationBar
import com.blockchain.componentlib.navigation.NavigationBarButton
import com.blockchain.componentlib.system.ShimmerLoadingCard
import com.blockchain.componentlib.tablerow.BalanceChangeTableRow
import com.blockchain.componentlib.tablerow.ValueChange
import com.blockchain.componentlib.tablerow.custom.StackedIcon
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.data.DataResource
import com.blockchain.data.map
import com.blockchain.home.domain.AssetFilter
import com.blockchain.home.presentation.R
import com.blockchain.home.presentation.SectionSize
import com.blockchain.home.presentation.allassets.AssetsIntent
import com.blockchain.home.presentation.allassets.AssetsViewModel
import com.blockchain.home.presentation.allassets.AssetsViewState
import com.blockchain.home.presentation.allassets.CryptoAssetState
import com.blockchain.koin.payloadScope
import info.blockchain.balance.FiatCurrency
import info.blockchain.balance.Money
import kotlinx.coroutines.launch
import org.koin.androidx.compose.getViewModel

@Composable
fun CryptoAssets(
    viewModel: AssetsViewModel = getViewModel(scope = payloadScope)
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val stateFlowLifecycleAware = remember(viewModel.viewState, lifecycleOwner) {
        viewModel.viewState.flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
    }
    val viewState: AssetsViewState? by stateFlowLifecycleAware.collectAsState(null)

    DisposableEffect(key1 = viewModel) {
        viewModel.onIntent(AssetsIntent.LoadAccounts(SectionSize.All))
        viewModel.onIntent(AssetsIntent.LoadFilters)
        onDispose { }
    }

    viewState?.let { state ->
        CryptoAssetsScreen(
            cryptoAssets = state.cryptoAssets,
            onSearchTermEntered = { term ->
                viewModel.onIntent(AssetsIntent.FilterSearch(term = term))
            },
            filters = state.filters,
            onFiltersConfirmed = { filters ->
                viewModel.onIntent(AssetsIntent.UpdateFilters(filters = filters))
            }
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CryptoAssetsScreen(
    cryptoAssets: DataResource<List<CryptoAssetState>>,
    onSearchTermEntered: (String) -> Unit,
    filters: List<AssetFilter>,
    onFiltersConfirmed: (List<AssetFilter>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
        confirmStateChange = { it != ModalBottomSheetValue.HalfExpanded }
    )
    val coroutineScope = rememberCoroutineScope()

    val focusManager = LocalFocusManager.current

    BackHandler(sheetState.isVisible) {
        coroutineScope.launch { sheetState.hide() }
    }

    ModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            CryptoAssetsFilters(
                filters = filters,
                onConfirmClick = { filters ->
                    coroutineScope.launch { sheetState.hide() }
                    onFiltersConfirmed(filters)
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color(0XFFF1F2F7))
        ) {
            NavigationBar(
                title = stringResource(R.string.ma_home_assets_title),
                onBackButtonClick = { },
                navigationBarButtons = listOf(
                    NavigationBarButton.Icon(
                        drawable = R.drawable.ic_filter,
                        color = null,
                        contentDescription = R.string.accessibility_filter
                    ) {
                        focusManager.clearFocus(true)
                        coroutineScope.launch { sheetState.show() }
                    }
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppTheme.dimensions.smallSpacing)
            ) {
                when (cryptoAssets) {
                    is DataResource.Loading -> {
                        ShimmerLoadingCard()
                    }
                    is DataResource.Error -> {
                        // todo
                    }
                    is DataResource.Data -> {
                        CryptoAssetsData(
                            cryptoAssets = cryptoAssets.data,
                            onSearchTermEntered = onSearchTermEntered
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CryptoAssetsData(
    cryptoAssets: List<CryptoAssetState>,
    onSearchTermEntered: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        CancelableOutlinedSearch(
            onValueChange = onSearchTermEntered,
            placeholder = stringResource(R.string.search)
        )

        Spacer(modifier = Modifier.size(AppTheme.dimensions.standardSpacing))

        CryptoAssetsList(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            cryptoAssets = cryptoAssets
        )
    }
}

@Composable
fun CryptoAssetsList(
    modifier: Modifier = Modifier,
    cryptoAssets: List<CryptoAssetState>
) {
    Card(
        backgroundColor = AppTheme.colors.background,
        shape = RoundedCornerShape(AppTheme.dimensions.mediumSpacing),
        elevation = 0.dp
    ) {
        if (cryptoAssets.isNotEmpty()) {
            Column(modifier = modifier) {
                cryptoAssets.forEachIndexed { index, cryptoAsset ->
                    BalanceChangeTableRow(
                        name = cryptoAsset.name,
                        value = cryptoAsset.fiatBalance.map {
                            it.toStringWithSymbol()
                        },
                        valueChange = cryptoAsset.change,
                        contentStart = {
                            CustomStackedIcon(
                                icon = if (cryptoAsset.icon.size == 2) {
                                    StackedIcon.SmallTag(
                                        main = ImageResource.Remote(
                                            cryptoAsset.icon[0]
                                        ),
                                        tag = ImageResource.Remote(
                                            cryptoAsset.icon[1]
                                        )
                                    )
                                } else {
                                    StackedIcon.SingleIcon(
                                        icon = ImageResource.Remote(cryptoAsset.icon[0])
                                    )
                                }
                            )
                        },
                        onClick = { /*todo coinview*/ }
                    )
                    if (index < cryptoAssets.lastIndex) {
                        Divider(color = Color(0XFFF1F2F7))
                    }
                }
            }
        } else {
            CryptoAssetsNoResults()
        }
    }
}

@Composable
fun CryptoAssetsNoResults() {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppTheme.dimensions.smallSpacing),
        text = stringResource(R.string.assets_no_result),
        style = AppTheme.typography.body2,
        color = AppTheme.colors.title,
        textAlign = TextAlign.Center
    )
}

@Preview(backgroundColor = 0xFF272727)
@Composable
fun PreviewCryptoAssetsScreen() {
    CryptoAssetsScreen(
        cryptoAssets = DataResource.Data(
            listOf(
                CryptoAssetState(
                    icon = listOf(""),
                    name = "Ethereum",
                    balance = DataResource.Data(Money.fromMajor(FiatCurrency.Dollars, 306.28.toBigDecimal())),
                    change = DataResource.Data(ValueChange.Up(3.94)),
                    fiatBalance = DataResource.Data(Money.fromMajor(FiatCurrency.Dollars, 306.28.toBigDecimal()))
                ),
                CryptoAssetState(
                    icon = listOf(""),
                    name = "Bitcoin",
                    balance = DataResource.Loading,
                    change = DataResource.Loading,
                    fiatBalance = DataResource.Loading
                ),
                CryptoAssetState(
                    icon = listOf(""),
                    name = "Solana",
                    balance = DataResource.Data(Money.fromMajor(FiatCurrency.Dollars, 306.28.toBigDecimal())),
                    change = DataResource.Data(ValueChange.Down(2.32)),
                    fiatBalance = DataResource.Data(Money.fromMajor(FiatCurrency.Dollars, 306.28.toBigDecimal()))
                )
            )
        ),
        onSearchTermEntered = {},
        filters = listOf(),
        {}
    )
}

@Preview(backgroundColor = 0xFF272727)
@Composable
fun PreviewCryptoAssetsScreen_Empty() {
    CryptoAssetsScreen(
        cryptoAssets = DataResource.Data(
            listOf()
        ),
        onSearchTermEntered = {},
        filters = listOf(),
        {}
    )
}
