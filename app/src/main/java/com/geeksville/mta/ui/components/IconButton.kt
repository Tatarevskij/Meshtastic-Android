package com.geeksville.mta.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.geeksville.mta.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun IconButton(
    onClick: () -> Unit,
    @DrawableRes drawableRes: Int,
    @StringRes contentDescription: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        drawableRes = drawableRes,
        contentDescription = stringResource(contentDescription),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    @DrawableRes drawableRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
    ) {
        Icon(
            painterResource(id = drawableRes),
            contentDescription,
            modifier = Modifier.scale(1.5f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IconButtonsPreview() {
    IconButton(
        onClick = {},
        drawableRes = R.drawable.ic_twotone_layers_24,
        R.string.map_style_selection,
    )
}
