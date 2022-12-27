package com.blockchain.componentlib.navigation

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blockchain.componentlib.theme.AppSurface
import com.blockchain.componentlib.theme.AppTheme
import com.blockchain.componentlib.utils.BaseAbstractComposeView

class BottomNavigationBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : BaseAbstractComposeView(context, attrs, defStyleAttr) {

    var navigationItems by mutableStateOf(
        listOf<NavigationItem>()
    )
    var onNavigationItemClick by mutableStateOf({ _: NavigationItem -> })
    var onMiddleButtonClick by mutableStateOf({})
    var hasMiddleButton by mutableStateOf(false)
    var selectedNavigationItem by mutableStateOf(null as? NavigationItem?)
    var bottomNavigationState by mutableStateOf(BottomNavigationState.Add)
    var isPulseAnimationEnabled by mutableStateOf(false)

    @Composable
    override fun Content() {

        AppTheme(setSystemColors = false) {
            AppSurface {
                BottomNavigationBar(
                    navigationItems,
                    onNavigationItemClick,
                    hasMiddleButton,
                    onMiddleButtonClick,
                    selectedNavigationItem,
                    bottomNavigationState,
                    isPulseAnimationEnabled && hasMiddleButton
                )
            }
        }
    }

    fun clearState() {
        navigationItems = emptyList()
        onNavigationItemClick = { _: NavigationItem -> }
        onMiddleButtonClick = {}
        selectedNavigationItem = null
        bottomNavigationState = BottomNavigationState.Add
        isPulseAnimationEnabled = false
    }
}
