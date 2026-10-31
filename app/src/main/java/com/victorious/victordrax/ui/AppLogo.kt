package com.victorious.victordrax.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.victorious.victordrax.R

/** VictorDrax logo: neon crown V with >_ cursor (v2 art). */
@Composable
fun AppLogo(size: Dp = 72.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size / 4))) {
        Image(painterResource(R.drawable.victordrax_logo), "VictorDrax logo", Modifier.fillMaxSize())
    }
}
