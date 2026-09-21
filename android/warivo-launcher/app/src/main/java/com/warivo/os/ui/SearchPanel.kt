package com.warivo.os.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.warivo.os.ui.theme.CardRadius
import com.warivo.os.ui.theme.GridGap
import com.warivo.os.ui.theme.WarivoAqua
import com.warivo.os.ui.theme.WarivoBlack
import com.warivo.os.ui.theme.WarivoSurface
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim

/**
 * The only window on the internet the rider gets.
 *
 * An in-app WebView rather than a browser intent, for two reasons: Lock Task blocks
 * launching other apps anyway, and keeping it in-process means the allowlist below is
 * the whole story — a tapped link cannot escape into a general-purpose browser.
 *
 * The full-width pill field with a round action button is lifted straight from the
 * reference dashboards' destination bar.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SearchPanel() {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true       // Google search needs it
            settings.domStorageEnabled = true
            settings.builtInZoomControls = false
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val host = request.url.host ?: return true
                    // Block anything off Google: a search result you tap must not turn
                    // the head unit into a general browser.
                    return !isAllowedHost(host)
                }
            }
            loadUrl("https://www.google.com/")
        }
    }

    fun submit() {
        webView.search(query)
        keyboard?.hide()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        SearchField(
            query = query,
            onQueryChange = { query = it },
            onSubmit = ::submit,
        )
        WarivoCard(modifier = Modifier.fillMaxSize(), contentPadding = false) {
            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(CardRadius)),
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(WarivoSurface)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = WarivoTextDim, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Search Google", color = WarivoTextDim, style = MaterialTheme.typography.bodyLarge)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = WarivoText, fontSize = 16.sp),
                cursorBrush = SolidColor(WarivoAqua),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // The round accent button, as in the reference destination bars.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .background(WarivoAqua)
                .clickableTile(onSubmit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = "Search",
                tint = WarivoBlack,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun WebView.search(query: String) {
    if (query.isBlank()) return
    loadUrl("https://www.google.com/search?q=" + Uri.encode(query))
}

private fun isAllowedHost(host: String): Boolean =
    host == "google.com" || host.endsWith(".google.com") ||
        host == "google.co.in" || host.endsWith(".google.co.in") ||
        host.endsWith(".gstatic.com")
