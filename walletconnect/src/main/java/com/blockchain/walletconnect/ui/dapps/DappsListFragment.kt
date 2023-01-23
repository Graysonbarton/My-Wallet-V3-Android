package com.blockchain.walletconnect.ui.dapps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.blockchain.commonarch.presentation.base.updateToolbar
import com.blockchain.commonarch.presentation.mvi.MviComposeFragment
import com.blockchain.componentlib.basic.Image
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.sheets.BottomSheetButton
import com.blockchain.componentlib.sheets.BottomSheetHostLayout
import com.blockchain.componentlib.sheets.BottomSheetOneButton
import com.blockchain.componentlib.sheets.BottomSheetTwoButtons
import com.blockchain.componentlib.sheets.ButtonType
import com.blockchain.componentlib.tablerow.DefaultTableRow
import com.blockchain.componentlib.theme.AppSurface
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.presentation.koin.scopedInject
import com.blockchain.walletconnect.R
import com.blockchain.walletconnect.domain.WalletConnectAnalytics
import com.blockchain.walletconnect.domain.WalletConnectSession

class DappsListFragment :
    MviComposeFragment<DappsListModel, DappsListIntent, DappsListState>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.process(DappsListIntent.LoadDapps)
    }

    companion object {
        fun newInstance() = DappsListFragment()
    }

    override fun onResume() {
        super.onResume()
        updateToolbar(
            toolbarTitle = getString(R.string.account_wallet_connect),
            menuItems = emptyList()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics.logEvent(
            WalletConnectAnalytics.ConnectedDappsListViewed
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme(setSystemColors = false) {
                    AppSurface(color = Color.White) {
                        Dapps(model)
                    }
                }
            }
        }
    }

    override val model: DappsListModel by scopedInject()

    @OptIn(ExperimentalMaterialApi::class)
    @Composable
    private fun Dapps(model: DappsListModel) {
        val state by model.state.subscribeAsState(null)

        var currentBottomSheet: SessionBottomSheet? by remember {
            mutableStateOf(null)
        }

        val modalBottomSheetState = rememberModalBottomSheetState(
            ModalBottomSheetValue.Hidden
        )

        var bottomSheetState by remember { mutableStateOf(ModalBottomSheetValue.Hidden) }

        BottomSheetHostLayout(
            modalBottomSheetState = modalBottomSheetState,
            onBackAction = { bottomSheetState = ModalBottomSheetValue.Hidden },
            stateFlow = bottomSheetState,
            sheetContent = {
                Spacer(modifier = Modifier.height(1.dp))
                currentBottomSheet?.let {
                    SheetLayout(
                        closeSheet = {
                            bottomSheetState = ModalBottomSheetValue.Hidden
                        },
                        bottomSheet = it
                    )
                }
            }, content = {
                state?.let {
                    if (it.connectedSessions.isEmpty()) {
                        RenderNoDapps()
                    } else {
                        DappsList(it.connectedSessions) { session ->
                            currentBottomSheet = SessionBottomSheet.Disconnect(
                                session = session,
                                onDisconnectClick = {
                                    currentBottomSheet = SessionBottomSheet.Confirmation(
                                        session,
                                        onConfirmClick = {
                                            model.process(DappsListIntent.Disconnect(session))
                                            bottomSheetState = ModalBottomSheetValue.Hidden
                                            analytics.logEvent(
                                                WalletConnectAnalytics.ConnectedDappActioned(
                                                    dappName = session.dAppInfo.peerMeta.name,
                                                    action =
                                                    WalletConnectAnalytics.DappConnectionAction.DISCONNECT_INTENT
                                                )
                                            )
                                        }
                                    )
                                    bottomSheetState = ModalBottomSheetValue.Hidden
                                    bottomSheetState = ModalBottomSheetValue.Expanded

                                    analytics.logEvent(
                                        WalletConnectAnalytics.ConnectedDappActioned(
                                            dappName = session.dAppInfo.peerMeta.name,
                                            action = WalletConnectAnalytics.DappConnectionAction.DISCONNECT
                                        )
                                    )
                                }
                            )
                            bottomSheetState = ModalBottomSheetValue.Expanded

                            analytics.logEvent(
                                WalletConnectAnalytics.ConnectedDappClicked(
                                    dappName = session.dAppInfo.peerMeta.name
                                )
                            )
                        }
                    }
                }
            },
            onCollapse = {
                bottomSheetState = ModalBottomSheetValue.Hidden
            }
        )
    }
}

@Composable
private fun SheetLayout(
    bottomSheet: SessionBottomSheet,
    closeSheet: () -> Unit
) {
    when (bottomSheet) {
        is SessionBottomSheet.Disconnect -> DisconnectBottomSheet(
            closeSheet = closeSheet,
            session = bottomSheet.session,
            onDisconnectClick = bottomSheet.onDisconnectClick
        )
        is SessionBottomSheet.Confirmation ->
            ConfirmActionBottomSheet(
                closeSheet = closeSheet,
                onConfirmationClick = bottomSheet.onConfirmClick,
                session = bottomSheet.session
            )
    }
}

@Composable
private fun ConfirmActionBottomSheet(
    closeSheet: () -> Unit,
    onConfirmationClick: () -> Unit,
    session: WalletConnectSession
) {
    BottomSheetTwoButtons(
        onCloseClick = closeSheet,
        title = stringResource(R.string.are_you_sure),
        subtitle = stringResource(R.string.you_are_about_disconnect, session.dAppInfo.peerMeta.name),
        headerImageResource = ImageResource.Local(R.drawable.ic_warning),
        button1 = BottomSheetButton(
            type = ButtonType.DESTRUCTIVE_MINIMAL,
            text = stringResource(R.string.common_disconnect),
            onClick = onConfirmationClick
        ),
        button2 = BottomSheetButton(
            type = ButtonType.MINIMAL,
            text = stringResource(R.string.common_cancel),
            onClick = closeSheet
        )
    )
}

@Composable
private fun DisconnectBottomSheet(
    closeSheet: () -> Unit,
    onDisconnectClick: () -> Unit,
    session: WalletConnectSession
) {
    BottomSheetOneButton(
        onCloseClick = closeSheet,
        title = session.dAppInfo.peerMeta.name,
        subtitle = session.dAppInfo.peerMeta.description,
        headerImageResource = ImageResource.Remote(session.dAppInfo.peerMeta.uiIcon()),
        shouldShowHeaderDivider = false,
        button = BottomSheetButton(
            type = ButtonType.DESTRUCTIVE_MINIMAL,
            text = stringResource(R.string.common_disconnect),
            onClick = onDisconnectClick
        )
    )
}

@Composable
private fun DappsList(sessions: List<WalletConnectSession>, onClick: (WalletConnectSession) -> Unit) {
    LazyColumn {
        items(
            items = sessions,
            itemContent = {
                DappListItem(it) {
                    onClick(it)
                }
            }
        )
    }
}

@Composable
private fun DappListItem(session: WalletConnectSession, onClick: () -> Unit) {
    Column {
        DefaultTableRow(
            startImageResource = ImageResource.Remote(
                session.dAppInfo.peerMeta.uiIcon()
            ),
            primaryText = session.dAppInfo.peerMeta.name,
            secondaryText = session.dAppInfo.peerMeta.url,
            endImageResource = ImageResource.Local(R.drawable.ic_more_horizontal),
            onClick = onClick
        )
        Divider(color = AppTheme.colors.light, thickness = 1.dp)
    }
}

@Preview(showBackground = true)
@Composable
private fun RenderNoDapps() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = dimensionResource(R.dimen.size_epic)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            contentScale = ContentScale.None,
            imageResource = ImageResource.Local(R.drawable.ic_world_blue)
        )
        Spacer(Modifier.size(dimensionResource(R.dimen.small_spacing)))
        Text(
            text = stringResource(R.string.no_dapps_connected),
            modifier = Modifier.padding(
                start = dimensionResource(R.dimen.small_spacing),
                end = dimensionResource(R.dimen.small_spacing)
            ),
            textAlign = TextAlign.Center,
            style = AppTheme.typography.title3,
            color = AppTheme.colors.title
        )
        Text(
            text = stringResource(R.string.connect_with_wallet_connect),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                start = dimensionResource(R.dimen.standard_spacing),
                end = dimensionResource(R.dimen.standard_spacing)
            ),
            style = AppTheme.typography.paragraph1,
            color = AppTheme.colors.body
        )
    }
}

sealed class SessionBottomSheet {
    abstract val session: WalletConnectSession

    class Disconnect(override val session: WalletConnectSession, val onDisconnectClick: () -> Unit) :
        SessionBottomSheet()

    class Confirmation(override val session: WalletConnectSession, val onConfirmClick: () -> Unit) :
        SessionBottomSheet()
}
