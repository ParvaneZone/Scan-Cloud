package com.example.cfscanner

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.cfscanner.xray.XrayRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("p", MODE_PRIVATE)
        XrayRunner.cleanupLeftovers(filesDir)

        setContent {
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
            var fa by remember { mutableStateOf(prefs.getBoolean("fa", true)) }
            var showJoin by remember { mutableStateOf(!prefs.getBoolean("joined", false)) }
            val uri = LocalUriHandler.current

            MaterialTheme(
                colorScheme = if (dark) darkColorScheme() else lightColorScheme()
            ) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (fa) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    }
                ) {
                    Surface(Modifier.fillMaxSize()) {
                        ScannerScreen(
                            fa = fa,
                            dark = dark,
                            onFa = {
                                fa = it
                                prefs.edit().putBoolean("fa", it).apply()
                            },
                            onDark = {
                                dark = it
                                prefs.edit().putBoolean("dark", it).apply()
                            }
                        )

                        if (showJoin) {
                            AlertDialog(
                                onDismissRequest = { showJoin = false },
                                title = { Text(tr("join_t", fa)) },
                                text = { Text(tr("join_msg", fa)) },
                                confirmButton = {
                                    TextButton({
                                        prefs.edit().putBoolean("joined", true).apply()
                                        showJoin = false
                                        uri.openUri(CHANNEL)
                                    }) {
                                        Text(tr("join", fa))
                                    }
                                },
                                dismissButton = {
                                    TextButton({ showJoin = false }) {
                                        Text(tr("later", fa))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScannerScreen(
    fa: Boolean,
    dark: Boolean,
    onFa: (Boolean) -> Unit,
    onDark: (Boolean) -> Unit
) {
    fun t(key: String) = tr(key, fa)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var tab by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    val currentVersion = remember {
        runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?: "0"
        }.getOrDefault("0")
    }

    fun checkUpdate() {
        updateMessage = t("checking")
        updateUrl = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { fetchLatest() }
            updateMessage = when {
                result == null -> t("upd_err")
                !isNewer(result.first, currentVersion) -> t("upd_none")
                result.second == null -> t("upd_noapk")
                else -> "${t("upd_new")} ${result.first}"
            }
            updateUrl = result?.second
        }
    }

    fun startUpdate(url: String) {
        if (Build.VERSION.SDK_INT >= 26 &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            updateMessage = t("allow_install")
            updateUrl = null
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        updateMessage = t("downloading")
        updateUrl = null
        scope.launch {
            try {
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle("Parvane Scanner")
                    .setDestinationInExternalFilesDir(
                        context,
                        Environment.DIRECTORY_DOWNLOADS,
                        "update-${System.currentTimeMillis()}.apk"
                    )
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                val id = manager.enqueue(request)

                while (true) {
                    val query = DownloadManager.Query().setFilterById(id)
                    manager.query(query).use { cursor ->
                        if (!cursor.moveToFirst()) {
                            throw Exception("missing")
                        }

                        val status = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        )
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val apkUri = manager.getUriForDownloadedFile(id)
                                ?: throw Exception("missing apk uri")
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, apkUri)
                                    .setDataAndType(
                                        apkUri,
                                        "application/vnd.android.package-archive"
                                    )
                                    .addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    )
                            )
                            updateMessage = ""
                            return@launch
                        }

                        if (status == DownloadManager.STATUS_FAILED) {
                            throw Exception("failed")
                        }
                    }
                    delay(800)
                }
            } catch (_: Exception) {
                updateMessage = ""
                uri.openUri(url)
            }
        }
    }

    if (updateMessage.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { updateMessage = "" },
            title = { Text(t("check_update")) },
            text = { Text(updateMessage) },
            confirmButton = {
                if (updateUrl != null) {
                    TextButton({ startUpdate(updateUrl!!) }) {
                        Text(t("update"))
                    }
                } else {
                    TextButton({ updateMessage = "" }) {
                        Text(t("ok"))
                    }
                }
            },
            dismissButton = {
                if (updateUrl != null) {
                    TextButton({ updateMessage = "" }) {
                        Text(t("cancel"))
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton({ menu = true }) {
                        Icon(IconMenu, null)
                    }
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(t("check_update")) },
                            leadingIcon = { Icon(IconRefresh, null) },
                            onClick = {
                                menu = false
                                checkUpdate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("v$currentVersion") },
                            onClick = {},
                            enabled = false
                        )
                    }
                }

                Image(
                    painterResource(com.example.cfscanner.R.drawable.logo),
                    null,
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Parvane Scanner",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(t("dark"))
                Spacer(Modifier.width(4.dp))
                Switch(dark, onDark)
                TextButton({ onFa(!fa) }) {
                    Text(if (fa) "English" else "فارسی")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    IconCloud to "tab_cf",
                    IconGlobe to "tab_sni",
                    IconInfo to "tab_about"
                ).forEachIndexed { index, (icon, key) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, null) },
                        label = { Text(t(key)) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            for (index in 0..2) {
                Box(
                    if (tab == index) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .size(0.dp)
                            .clipToBounds()
                    }
                ) {
                    when (index) {
                        0 -> CfTab(fa)
                        1 -> SniTab(fa)
                        else -> AboutTab(fa)
                    }
                }
            }
        }
    }
}
