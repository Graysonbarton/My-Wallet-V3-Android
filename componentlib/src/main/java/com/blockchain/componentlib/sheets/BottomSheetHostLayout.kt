package com.blockchain.componentlib.sheets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.blockchain.componentlib.R
import com.blockchain.componentlib.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun BottomSheetHostLayout(
    content: @Composable () -> Unit,
    stateFlow: ModalBottomSheetValue,
    onBackAction: () -> Unit,
    modalBottomSheetState: ModalBottomSheetState = rememberModalBottomSheetState(stateFlow),
    sheetContent: @Composable ColumnScope.() -> Unit,
    onCollapse: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val openSheet = {
        scope.launch { modalBottomSheetState.show() }
    }

    val closeSheet = {
        scope.launch { modalBottomSheetState.hide() }
    }

    BackHandler(modalBottomSheetState.isVisible) {
        onBackAction()
    }

    LaunchedEffect(stateFlow) {
        if (stateFlow == ModalBottomSheetValue.Hidden) {
            closeSheet()
        } else if (stateFlow == ModalBottomSheetValue.Expanded) {
            openSheet()
        }
    }

    ModalBottomSheetLayout(
        modifier = Modifier.fillMaxSize(),
        sheetState = modalBottomSheetState,
        sheetShape = RoundedCornerShape(
            topEnd = dimensionResource(R.dimen.small_spacing),
            topStart = dimensionResource(R.dimen.small_spacing)
        ),
        sheetContent = sheetContent,
        scrimColor = AppTheme.colors.overlay
    ) {
        content()

        // detect collapse
        val collapseFraction = modalBottomSheetState.progress.fraction
        val targetValue = modalBottomSheetState.targetValue
        val currentValue = modalBottomSheetState.currentValue

        if (currentValue == ModalBottomSheetValue.Expanded &&
            targetValue == ModalBottomSheetValue.Hidden &&
            collapseFraction < 1.0 &&
            collapseFraction > 0.9F
        ) {
            onCollapse()
        }
    }
}
