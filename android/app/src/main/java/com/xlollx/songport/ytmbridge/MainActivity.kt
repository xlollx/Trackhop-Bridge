package com.xlollx.songport.ytmbridge

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.concurrent.thread
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.xlollx.songport.ytmbridge.app.BuildConfig
import com.xlollx.songport.ytmbridge.app.R
import com.xlollx.songport.ytmbridge.ui.BridgeTheme
import com.xlollx.songport.ytmbridge.ui.HeroHeader
import com.xlollx.songport.ytmbridge.R as BridgeR

/** Status screen: what this app is, the warning, connect / disconnect, back to Trackhop. */
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) { super.attachBaseContext(AppLocale.wrap(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BridgeTheme {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }
}

/** The languages offered, as in Trackhop; "" is the system language. */
private val LANGUAGES = listOf("", "en", "it", "fr", "de", "es", "pt-BR", "pl", "ru", "tr", "zh", "ja", "ko")

@Composable
private fun Screen() {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { version++; onPauseOrDispose { } }
    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { version++ }
    val connected = remember(version) { Session.isConnected(ctx) }
    val account = remember(version) { Session.account(ctx) }
    var showDisclaimer by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroHeader(stringResource(R.string.app_name), stringResource(R.string.version, BuildConfig.VERSION_NAME))
        Text(stringResource(R.string.intro), style = MaterialTheme.typography.bodyLarge)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.warning_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(R.string.warning_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = { showDisclaimer = true }) { Text(stringResource(R.string.disclaimer_review)) }
            }
        }
        if (showDisclaimer) AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text(stringResource(BridgeR.string.bridge_disclaimer_title)) },
            text = { Text(stringResource(BridgeR.string.bridge_disclaimer_body), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { Disclaimer.accept(ctx); showDisclaimer = false }) { Text(stringResource(BridgeR.string.bridge_disclaimer_accept)) } },
        )

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.ytm_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (connected) stringResource(R.string.status_connected, account ?: "YouTube Music") else stringResource(R.string.status_disconnected),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (connected) {
                        OutlinedButton(onClick = { Session.clear(ctx); version++ }) { Text(stringResource(R.string.disconnect)) }
                    } else {
                        Button(onClick = { login.launch(Intent(ctx, LoginActivity::class.java)) }) { Text(stringResource(R.string.connect)) }
                    }
                }
                // Google's abuse page seen recently: only the user can pass its captcha.
                if (remember(version) { YtmClient.Verification.pending(ctx) }) {
                    Text(stringResource(R.string.verify_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { login.launch(Intent(ctx, VerifyActivity::class.java)) }) { Text(stringResource(BridgeR.string.bridge_verify_google)) }
                }
            }
        }

        WebServiceCard(
            title = stringResource(R.string.spotify_title), intro = stringResource(R.string.spotify_intro),
            connectLabel = stringResource(R.string.spotify_connect), session = SpotifyBridge.session,
            loginIntent = WebLoginActivity.intent(ctx, WebLoginActivity.SPOTIFY), version = version, onChanged = { version++ },
        )
        WebServiceCard(
            title = stringResource(R.string.apple_title), intro = stringResource(R.string.apple_intro),
            connectLabel = stringResource(R.string.apple_connect), session = AppleBridge.session,
            loginIntent = WebLoginActivity.intent(ctx, WebLoginActivity.APPLE), version = version, onChanged = { version++ },
        )

        AmazonCard(version, onChanged = { version++ })

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                // Trackhop keeps the package id it had as Songport.
                val open = ctx.packageManager.getLaunchIntentForPackage("com.xlollx.songport")
                if (open != null) ctx.startActivity(open)
                else ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xlollx/Trackhop")))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.open_trackhop)) }
        TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xlollx/Songport-YTM-Bridge"))) }) {
            Text(stringResource(R.string.source_code))
        }
        var langOpen by remember { mutableStateOf(false) }
        TextButton(onClick = { langOpen = true }) {
            Text(stringResource(R.string.language) + ": " + AppLocale.label(ctx, AppLocale.current(ctx)))
        }
        if (langOpen) AlertDialog(
            onDismissRequest = { langOpen = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    LANGUAGES.forEach { tag ->
                        TextButton(onClick = { langOpen = false; (ctx as? android.app.Activity)?.let { AppLocale.set(it, tag) } }, modifier = Modifier.fillMaxWidth()) {
                            Text(AppLocale.label(ctx, tag))
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

/** A web-session service (Spotify, Apple Music): status, sign in, sign out. */
@Composable
private fun WebServiceCard(
    title: String, intro: String, connectLabel: String, session: WebSession, loginIntent: Intent,
    version: Int, onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onChanged() }
    val connected = remember(version) { session.isConnected(ctx) }
    val account = remember(version) { session.account(ctx) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(intro, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (connected) stringResource(R.string.status_connected, account ?: title) else stringResource(R.string.status_disconnected),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connected) OutlinedButton(onClick = { session.clear(ctx); onChanged() }) { Text(stringResource(R.string.disconnect)) }
                else Button(onClick = { login.launch(loginIntent) }) { Text(connectLabel) }
            }
        }
    }
}

/** Amazon Music, experimental: sign-in, a connection test and the traffic capture used to finish the connector. */
@Composable
private fun AmazonCard(version: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onChanged() }
    val connected = remember(version) { AmazonSession.isConnected(ctx) }
    val account = remember(version) { AmazonSession.account(ctx) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(BridgeR.string.bridge_amazon_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.amazon_intro), style = MaterialTheme.typography.bodyMedium)
            Text(
                when { !connected -> stringResource(R.string.status_disconnected); account != null -> stringResource(R.string.status_connected, account); else -> stringResource(R.string.status_connected_plain) },
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connected) {
                    OutlinedButton(onClick = { AmazonSession.clear(ctx); testResult = null; onChanged() }) { Text(stringResource(R.string.disconnect)) }
                    Button(enabled = !testing, onClick = {
                        testing = true
                        testResult = ctx.getString(R.string.amazon_test_running)
                        val app = ctx.applicationContext
                        thread {
                            val text = try {
                                // A test always starts afresh: no cached credentials or configuration.
                                AmazonBridge.forget()
                                AmazonClient.forgetConfig()
                                val client = AmazonClient(app)
                                val found = client.searchTracks("Daft Punk")
                                val who = client.accountName() ?: AmazonSession.account(app)
                                if (who != null) app.getString(R.string.amazon_test_ok, who, found.size)
                                else app.getString(R.string.amazon_test_ok_noname, found.size)
                            } catch (e: Exception) {
                                app.getString(R.string.amazon_test_fail, e.message ?: e.javaClass.simpleName)
                            }
                            testResult = text
                            testing = false
                        }
                    }) { Text(stringResource(R.string.amazon_test)) }
                } else {
                    Button(onClick = { login.launch(Intent(ctx, AmazonLoginActivity::class.java)) }) { Text(stringResource(R.string.amazon_connect)) }
                }
            }
            testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (connected) {
                TextButton(onClick = { ctx.startActivity(Intent(ctx, AmazonCaptureActivity::class.java)) }) { Text(stringResource(BridgeR.string.bridge_amazon_capture)) }
            }
        }
    }
}
