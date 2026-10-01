package com.example.cfscanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun AboutTab(fa: Boolean) {
    fun t(key: String) = tr(key, fa)

    val uri = LocalUriHandler.current
    Column(
        Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            t("about_t"),
            style = MaterialTheme.typography.titleMedium
        )
        Image(
            painterResource(com.example.cfscanner.R.drawable.logo),
            null,
            Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(20.dp))
                .align(Alignment.CenterHorizontally)
        )
        Text(t("about_text"))
        OutlinedButton(
            onClick = { uri.openUri(CHANNEL) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(IconUsers, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(t("channel"))
        }
        Button(
            onClick = { uri.openUri(DEV) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(IconSend, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(t("contact"))
        }
    }
}
