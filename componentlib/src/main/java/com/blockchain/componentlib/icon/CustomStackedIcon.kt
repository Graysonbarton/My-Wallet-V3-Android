package com.blockchain.componentlib.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import com.blockchain.componentlib.R
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.media.AsyncMediaItem
import com.blockchain.componentlib.tablerow.custom.StackedIcon
import com.blockchain.componentlib.theme.AppTheme

@OptIn(ExperimentalCoilApi::class)
@Composable
fun CustomStackedIcon(
    icon: StackedIcon,
    iconBackground: Color = AppTheme.colors.light,
    borderColor: Color = AppTheme.colors.background,
    size: Dp = 18.dp,
) {
    when (icon) {
        is StackedIcon.OverlappingPair -> OverlapIcon(
            icon = icon,
            iconSize = size,
            iconBackground = iconBackground,
            borderColor = borderColor
        )
        is StackedIcon.SmallTag -> SmallTagIcon(
            icon = icon,
            mainIconSize = size,
            iconBackground = iconBackground,
            borderColor = borderColor
        )
        is StackedIcon.SingleIcon -> AsyncMediaItem(
            modifier = Modifier
                .size(AppTheme.dimensions.standardSpacing)
                .background(color = AppTheme.colors.light, shape = CircleShape)
                .border(width = AppTheme.dimensions.noSpacing, Color.Transparent, shape = CircleShape),
            imageResource = icon.icon
        )
        StackedIcon.None -> {
            // n/a
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewCustomStackedIconSmallTag() {
    CustomStackedIcon(
        icon = StackedIcon.SmallTag(
            main = ImageResource.Local(R.drawable.ic_close_circle_dark),
            tag = ImageResource.Local(R.drawable.ic_close_circle)
        )
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewCustomStackedIconOverlapIcon() {
    CustomStackedIcon(
        icon = StackedIcon.OverlappingPair(
            front = ImageResource.Local(R.drawable.ic_close_circle_dark),
            back = ImageResource.Local(R.drawable.ic_close_circle)
        )
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewCustomStackedIconSingleIcon() {
    CustomStackedIcon(
        icon = StackedIcon.SingleIcon(ImageResource.Local(R.drawable.ic_close_circle_dark))
    )
}
