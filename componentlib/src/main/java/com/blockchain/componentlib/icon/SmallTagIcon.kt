package com.blockchain.componentlib.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.blockchain.componentlib.R
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.media.AsyncMediaItem
import com.blockchain.componentlib.tablerow.custom.StackedIcon
import com.blockchain.componentlib.theme.AppTheme

@Composable
fun SmallTagIcon(
    icon: StackedIcon.SmallTag,
    iconBackground: Color = AppTheme.colors.light,
    borderColor: Color = AppTheme.colors.background,
    mainIconSize: Dp = 24.dp
) {
    val borderSize = mainIconSize.div(12f)
    val tagIconSize = mainIconSize.div(2)
    val overlap = mainIconSize.times(.4f)

    Box(
        modifier = Modifier
            .size(mainIconSize + tagIconSize - overlap + borderSize)
    ) {

        AsyncMediaItem(
            modifier = Modifier
                .size(mainIconSize)
                .background(color = iconBackground, shape = CircleShape)
                .border(width = AppTheme.dimensions.noSpacing, Color.Transparent, shape = CircleShape),
            imageResource = icon.main
        )

        AsyncMediaItem(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(tagIconSize + borderSize * 2)
                .background(color = iconBackground, shape = CircleShape)
                .border(width = borderSize, borderColor, CircleShape)
                .padding(borderSize),
            imageResource = icon.tag
        )
    }
}

@Preview(backgroundColor = 0XFFF0F2F7, showBackground = true)
@Composable
fun PreviewSmallTagIcons() {
    SmallTagIcon(
        icon = StackedIcon.SmallTag(
            main = ImageResource.Local(R.drawable.ic_close_circle_dark),
            tag = ImageResource.Local(R.drawable.ic_close_circle)
        ),
        borderColor = AppTheme.colors.light
    )
}
