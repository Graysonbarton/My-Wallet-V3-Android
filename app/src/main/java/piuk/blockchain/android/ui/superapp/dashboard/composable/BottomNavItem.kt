package piuk.blockchain.android.ui.superapp.dashboard.composable

import piuk.blockchain.android.R

sealed class BottomNavItem(var title:String, var icon:Int, var screen_route:String){

    object Home : BottomNavItem("Home", R.drawable.ic_tab_home_demo,"home")
    object Trade: BottomNavItem("Card",R.drawable.ic_tab_itemtrade,"trade")
    object Card: BottomNavItem("Card",R.drawable.ic_tab_item_card,"card")
}