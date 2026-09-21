package com.warivo.os.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.warivo.os.ui.theme.CardBrush
import com.warivo.os.ui.theme.CardRadius
import com.warivo.os.ui.theme.ContentPadding
import com.warivo.os.ui.theme.WarivoAccent
import com.warivo.os.ui.theme.WarivoHairline
import com.warivo.os.ui.theme.WarivoText
import com.warivo.os.ui.theme.WarivoTextDim

/** Shortcut chips. Each is just a canned Google query, not a destination. */
private val SHORTCUTS = listOf<Pair<String, ImageVector>>(
    "Weather" to Icons.Filled.WbSunny,
    "Traffic near me" to Icons.Filled.DirectionsCar,
    "Petrol pump nearby" to Icons.Filled.NearMe,
    "News" to Icons.Filled.Article,
)

/**
 * The only window on the internet the rider gets, laid out as
 * branding/mockups/05-search.html: a wordmark, a wide pill field, shortcut chips and
 * recent searches — and the results WebView only once something is searched.
 *
 * An in-app WebView rather than a browser intent, for two reasons: Lock Task blocks
 * launching other apps anyway, and keeping it in-process means the allowlist below is the
 * whole story — a tapped result cannot escape into a general-purpose browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SearchPanel() {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var showResults by remember { mutableStateOf(false) }
    val recents = remember { mutableStateOf(listOf<String>()) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true       // Google search needs it
            settings.domStorageEnabled = true
            settings.builtInZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val host = request.url.host ?: return true
                    // Block anything off Google: a result you tap must not turn the head
                    // unit into a general browser.
                    return !isAllowedHost(host)
                }
            }
        }
    }

    fun run(text: String) {
        if (text.isBlank()) return
        query = text
        recents.value = (listOf(text) + recents.value.filterNot { it == text }).take(4)
        webView.loadUrl("https://www.google.com/search?q=" + Uri.encode(text))
        showResults = true
        keyboard?.hide()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ContentPadding)
            .padding(bottom = ContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showResults) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GhostCircleButton(Icons.Filled.ArrowBackIosNew, "Back to search", 48.dp) {
                    showResults = false
                }
                SearchBar(
                    query = query,
                    compact = true,
                    onQueryChange = { query = it },
                    onSubmit = { run(query) },
                )
            }
            WarivoCard(modifier = Modifier.fillMaxSize(), padded = false) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(CardRadius)),
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .widthIn(max = 1040.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            // The mockup sets this in Kaushan Script from branding/.fonts. Bundling the
            // TTF would match it exactly; the cursive fallback keeps the APK font-free.
            Text(
                "Warivo",
                color = WarivoText,
                fontSize = 82.sp,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Normal,
            )
            Text(
                "NOVA-S",
                color = WarivoTextDim,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "Effortless Elegance · Practical Luxury",
                color = WarivoTextDim,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp),
            )

            Spacer(Modifier.height(34.dp))
            SearchBar(
                query = query,
                compact = false,
                onQueryChange = { query = it },
                onSubmit = { run(query) },
            )

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SHORTCUTS.forEach { (text, icon) ->
                    ShortcutChip(text, icon) { run(text) }
                }
            }

            if (recents.value.isNotEmpty()) {
                Spacer(Modifier.height(34.dp))
                CardLabel(
                    "Recent",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, bottom = 6.dp),
                )
                WarivoCard(modifier = Modifier.fillMaxWidth(), padded = false) {
                    recents.value.forEachIndexed { index, text ->
                        if (index > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(WarivoHairline)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableTile { run(text) }
                                .padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = null,
                                tint = WarivoTextDim,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(text, color = WarivoText, fontSize = 20.sp, modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = WarivoTextDim,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** `.searchbar` — a tall pill with an aqua rim and a round accent button. */
@Composable
private fun SearchBar(
    query: String,
    compact: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val height = if (compact) 60.dp else 84.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(CardBrush, RoundedCornerShape(50))
            .border(1.5.dp, WarivoAccent.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(start = if (compact) 22.dp else 30.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = WarivoTextDim,
            modifier = Modifier.size(if (compact) 24.dp else 30.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    "Search Google",
                    color = WarivoTextDim,
                    fontSize = if (compact) 20.sp else 28.sp,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = WarivoText,
                    fontSize = if (compact) 20.sp else 28.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(WarivoAccent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AccentCircleButton(
            Icons.Filled.Search,
            "Search",
            if (compact) 44.dp else 60.dp,
            onSubmit,
        )
    }
}

/** `.chip-btn` — a canned query. */
@Composable
private fun ShortcutChip(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(CardBrush, RoundedCornerShape(22.dp))
            .border(1.dp, WarivoHairline, RoundedCornerShape(22.dp))
            .clickableTile(onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = WarivoAccent, modifier = Modifier.size(20.dp))
        Text(text, color = WarivoText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun isAllowedHost(host: String): Boolean =
    host == "google.com" || host.endsWith(".google.com") ||
        host == "google.co.in" || host.endsWith(".google.co.in") ||
        host.endsWith(".gstatic.com")
