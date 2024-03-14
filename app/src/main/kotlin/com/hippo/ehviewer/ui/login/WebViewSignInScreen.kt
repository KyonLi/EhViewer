package com.hippo.ehviewer.ui.login

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import com.google.accompanist.web.AccompanistWebViewClient
import com.google.accompanist.web.WebView
import com.google.accompanist.web.rememberWebViewState
import com.hippo.ehviewer.client.EhCookieStore
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.ui.LockDrawer
import com.hippo.ehviewer.ui.StartDestination
import com.hippo.ehviewer.ui.screen.popNavigate
import com.hippo.ehviewer.util.setDefaultSettings
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withNonCancellableContext
import eu.kanade.tachiyomi.util.lang.withUIContext
import java.util.Locale
import io.ktor.http.Url

@Destination
@Composable
fun WebViewSignInScreen(navigator: DestinationsNavigator) {
    LockDrawer(true)
    val coroutineScope = rememberCoroutineScope()
    val state = rememberWebViewState(url = EhUrl.URL_SIGN_IN)
    val client = remember {
        object : AccompanistWebViewClient() {
            private var present = false
            override fun onPageFinished(view: WebView, url: String?) {
                if (present) {
                    view.destroy()
                    return
                }
                var getId = false
                var getHash = false
                EhCookieStore.load(Url(EhUrl.HOST_E)).forEach {
                    if (EhCookieStore.KEY_IPB_MEMBER_ID == it.name) {
                        getId = true
                    } else if (EhCookieStore.KEY_IPB_PASS_HASH == it.name) {
                        getHash = true
                    }
                }
                if (getId && getHash) {
                    present = true
                    coroutineScope.launchIO {
                        withNonCancellableContext { postLogin() }
                        withUIContext { navigator.popNavigate(StartDestination) }
                    }
                }
            }
        }
    }
    SideEffect {
        EhUtils.signOut()
    }
    WebView(
        state = state,
        modifier = Modifier.fillMaxSize(),
        onCreated = {
            it.setDefaultSettings()
        },
        client = client,
    )


    val clipboardManager = LocalClipboardManager.current
    var ipbMemberId by rememberSaveable { mutableStateOf("") }
    var ipbPassHash by rememberSaveable { mutableStateOf("") }
    var igneous by rememberSaveable { mutableStateOf("") }
    fun storeCookie(id: String, hash: String, igneous: String) {
        EhUtils.signOut()
        EhCookieStore.addCookie(EhCookieStore.KEY_IPB_MEMBER_ID, id, EhUrl.DOMAIN_E)
        EhCookieStore.addCookie(EhCookieStore.KEY_IPB_PASS_HASH, hash, EhUrl.DOMAIN_E)
        if (igneous.isNotBlank() && igneous != "mystery") {
            EhCookieStore.addCookie(EhCookieStore.KEY_IGNEOUS, igneous, EhUrl.DOMAIN_EX)
        }
    }
    fun getCookiesFromClipboard() {
        val text = clipboardManager.getText()
        if (text == null) {
            return
        }
        runCatching {
            val kvs: Array<String> = if (text.contains(";")) {
                text.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            } else if (text.contains("\n")) {
                text.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            } else {
                return
            }
            if (kvs.size < 2) {
                return
            }
            for (s in kvs) {
                val kv: Array<String> = if (s.contains("=")) {
                    s.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                } else if (s.contains(":")) {
                    s.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                } else {
                    continue
                }
                if (kv.size != 2) {
                    continue
                }
                when (kv[0].trim { it <= ' ' }.lowercase(Locale.getDefault())) {
                    "ipb_member_id" -> ipbMemberId = kv[1].trim { it <= ' ' }
                    "ipb_pass_hash" -> ipbPassHash = kv[1].trim { it <= ' ' }
                    "igneous" -> igneous = kv[1].trim { it <= ' ' }
                }
            }
            coroutineScope.launchIO {
                storeCookie(ipbMemberId, ipbPassHash, igneous)
                withNonCancellableContext { postLogin() }
                withUIContext { navigator.popNavigate(StartDestination) }
            }
        }.onFailure {
            it.printStackTrace()
        }
    }
    LaunchedEffect {
        getCookiesFromClipboard()
    }
}
