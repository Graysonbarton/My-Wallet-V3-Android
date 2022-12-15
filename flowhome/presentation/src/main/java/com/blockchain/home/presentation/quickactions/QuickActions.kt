package com.blockchain.home.presentation.quickactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import com.blockchain.coincore.AssetAction
import com.blockchain.componentlib.basic.Image
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.theme.AppSurface
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.home.presentation.R
import com.blockchain.home.presentation.navigation.AssetActionsNavigation
import com.blockchain.koin.payloadScope
import org.koin.androidx.compose.getViewModel

@Composable
fun QuickActions(
    viewModel: QuickActionsViewModel = getViewModel(scope = payloadScope),
    assetActionsNavigation: AssetActionsNavigation,
    openMoreQuickActions: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val stateFlowLifecycleAware = remember(viewModel.viewState, lifecycleOwner) {
        viewModel.viewState.flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
    }
    val viewState: QuickActionsViewState? by stateFlowLifecycleAware.collectAsState(null)

    DisposableEffect(key1 = viewModel) {
        viewModel.onIntent(QuickActionsIntent.LoadActions(ActionType.Quick))
        onDispose { }
    }

    viewState?.let {
        QuickActionsRow(
            quickActionItems = it.actions,
            onClick = { action ->
                when (action) {
                    is QuickAction.TxAction -> assetActionsNavigation.navigate(action.assetAction)
                    is QuickAction.More -> openMoreQuickActions()
                }
            }
        )
    }
}

@Composable
fun QuickActionsRow(quickActionItems: List<QuickActionItem>, onClick: (QuickAction) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        quickActionItems.forEach { quickAction ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = {
                    if (quickAction.enabled)
                        onClick(quickAction.action)
                    else {
                        // do nothing
                    }
                })
            ) {
                Image(
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 2.dp)
                        .alpha(
                            if (quickAction.enabled) 1f else .6f
                        ),
                    imageResource = ImageResource.Local(quickAction.icon)
                )
                Text(
                    text = stringResource(id = quickAction.title),
                    style = AppTheme.typography.caption1,
                    color = AppTheme.colors.muted,
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .alpha(
                            if (quickAction.enabled) 1f else .6f
                        )
                )
            }
        }
    }
}

@Preview
@Composable
fun QuickActionsPreview() {
    AppTheme {
        AppSurface {
            QuickActionsRow(
                listOf(
                    QuickActionItem(
                        title = R.string.common_buy,
                        icon = R.drawable.ic_buy,
                        action = QuickAction.TxAction(AssetAction.Swap),
                        enabled = false
                    ),
                    QuickActionItem(
                        title = R.string.common_buy,
                        icon = R.drawable.ic_buy,
                        enabled = true,
                        action = QuickAction.TxAction(AssetAction.Swap)
                    ),
                    QuickActionItem(
                        title = R.string.common_buy,
                        enabled = false,
                        icon = R.drawable.ic_buy,
                        action = QuickAction.TxAction(AssetAction.Swap)
                    )
                ),
                {}
            )
        }
    }
}
