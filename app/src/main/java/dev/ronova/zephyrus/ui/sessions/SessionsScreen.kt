package dev.ronova.zephyrus.ui.sessions

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ronova.zephyrus.container
import dev.ronova.zephyrus.data.Cookie
import dev.ronova.zephyrus.data.ProfileRow
import dev.ronova.zephyrus.data.SITES
import dev.ronova.zephyrus.data.Site
import dev.ronova.zephyrus.data.baseDomain
import dev.ronova.zephyrus.data.collectCookies
import dev.ronova.zephyrus.data.hostOf
import dev.ronova.zephyrus.data.siteByKey
import dev.ronova.zephyrus.data.siteIconDomain
import dev.ronova.zephyrus.data.siteLabel
import dev.ronova.zephyrus.engine.formatDate
import dev.ronova.zephyrus.ui.components.DropdownField
import dev.ronova.zephyrus.ui.components.Pill
import dev.ronova.zephyrus.ui.components.SectionCard
import dev.ronova.zephyrus.ui.components.SiteBadge
import androidx.compose.ui.res.stringResource
import dev.ronova.zephyrus.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen() {
    val context = LocalContext.current
    val c = context.container
    val cs = MaterialTheme.colorScheme
    val profiles by c.db.profiles.collectAsStateWithLifecycle()

    var loginSite by remember { mutableStateOf<Site?>(null) }
    var pending by remember { mutableStateOf<Pair<Site, List<Cookie>>?>(null) }
    var askCustom by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProfileRow?>(null) }
    var deleting by remember { mutableStateOf<ProfileRow?>(null) }
    var importText by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text != null) importText = text
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.file_read_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.nav_sessions), fontWeight = FontWeight.Bold) },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent, scrolledContainerColor = cs.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.sessions_intro),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(SITES, key = { it.key }) { site ->
                val mine = profiles.filter { it.site == site.key }
                SiteTile(
                    name = site.name, domain = site.iconDomain, letter = site.letter, color = site.color,
                    status = if (mine.isEmpty()) stringResource(R.string.no_profiles) else stringResource(R.string.profiles_count, mine.size),
                    active = mine.any { it.active },
                    onClick = { loginSite = site },
                )
            }
            item {
                SiteTile(
                    name = stringResource(R.string.other_site), domain = "", letter = "+", color = 0xFF9E9E9E,
                    status = stringResource(R.string.any_address), active = false, onClick = { askCustom = true },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.my_profiles), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { picker.launch(arrayOf("text/plain", "*/*")) }) { Text(stringResource(R.string.import_cookies)) }
                }
            }
            if (profiles.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.profiles_empty),
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                }
            }
            items(profiles, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { p ->
                ProfileCard(
                    p, onActivate = { c.sessions.setActive(p.id) },
                    onRename = { renaming = p }, onDelete = { deleting = p },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.sessions_note),
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    // ---------- вход через WebView ----------
    loginSite?.let { site ->
        LoginDialog(
            site = site,
            onDismiss = { loginSite = null },
            onSaved = { cookies ->
                loginSite = null
                pending = Pair(site, cookies)
            },
        )
    }

    // ---------- имя нового профиля ----------
    pending?.let { (site, cookies) ->
        val defaultName = stringResource(R.string.profile_default_name, profiles.count { it.site == site.key } + 1)
        var name by remember { mutableStateOf(defaultName) }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.profile_name_title)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.profile_name)) }) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        c.sessions.add(site.key, name.trim(), cookies)
                        pending = null
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    // ---------- другой сайт ----------
    if (askCustom) {
        var address by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askCustom = false },
            title = { Text(stringResource(R.string.other_site)) },
            text = {
                OutlinedTextField(
                    value = address, onValueChange = { address = it }, singleLine = true,
                    label = { Text(stringResource(R.string.login_address)) }, placeholder = { Text("https://example.com/login") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    var u = address.trim()
                    if (u.isNotEmpty()) {
                        if (!u.contains("://")) u = "https://$u"
                        val host = hostOf(u)
                        if (host.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.domain_unknown), Toast.LENGTH_SHORT).show()
                        } else {
                            val base = baseDomain(host)
                            loginSite = Site("custom:$base", host, u, listOf(base), 0xFF9E9E9E, "+", extraCookieUrls = listOf(u))
                            askCustom = false
                        }
                    }
                }) { Text(stringResource(R.string.open)) }
            },
            dismissButton = { TextButton(onClick = { askCustom = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    // ---------- переименование ----------
    renaming?.let { p ->
        var name by remember { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.profile_name)) }) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        c.sessions.rename(p.id, name.trim())
                        renaming = null
                    }
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    // ---------- удаление ----------
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_profile_title)) },
            text = { Text(stringResource(R.string.delete_profile_text, p.name)) },
            confirmButton = {
                TextButton(onClick = {
                    c.sessions.delete(p.id)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    // ---------- импорт cookies.txt ----------
    importText?.let { text ->
        ImportDialog(
            onDismiss = { importText = null },
            onConfirm = { siteKey, name, hosts ->
                try {
                    c.sessions.importNetscape(siteKey, name, text, hosts)
                    importText = null
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(context, context.getString(R.string.import_no_cookies), Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.import_failed), Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

@Composable
private fun SiteTile(
    name: String, domain: String, letter: String, color: Long,
    status: String, active: Boolean, onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SiteBadge(domain = domain, letter = letter, color = color, size = 52.dp)
            Text(name, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
            Text(
                (if (active) "● " else "") + status,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) cs.primary else cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileCard(p: ProfileRow, onActivate: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val site = siteByKey(p.site)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SiteBadge(siteIconDomain(p.site), site?.letter ?: "?", site?.color ?: 0xFF9E9E9E, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    "${siteLabel(p.site)} · cookies: ${p.cookieCount} · ${formatDate(p.createdAt)}",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2,
                )
                if (p.active) {
                    Pill(stringResource(R.string.active), Modifier.padding(top = 6.dp))
                } else {
                    TextButton(onClick = onActivate, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.make_active)) }
                }
            }
            IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.rename)) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = cs.error) }
        }
    }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onConfirm: (siteKey: String, name: String, hosts: List<String>?) -> Unit) {
    var site by remember { mutableStateOf(SITES.first().key) }
    var domain by remember { mutableStateOf("") }
    val defaultName = stringResource(R.string.imported_profile)
    var name by remember { mutableStateOf(defaultName) }
    val options = SITES.map { it.name to it.key } + (stringResource(R.string.other_site_dots) to "custom")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_cookies)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownField(stringResource(R.string.site), options, site, { site = it })
                if (site == "custom") {
                    OutlinedTextField(
                        value = domain, onValueChange = { domain = it }, singleLine = true,
                        label = { Text(stringResource(R.string.domain)) }, placeholder = { Text("example.com") },
                    )
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.profile_name_title)) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                if (site == "custom") {
                    val d = baseDomain(domain.trim().lowercase())
                    if (d.isNotEmpty()) onConfirm("custom:$d", name.trim(), listOf(d))
                } else {
                    onConfirm(site, name.trim(), null)
                }
            }) { Text(stringResource(R.string.import_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Полноэкранное окно входа на сайт. Cookies после входа забираются из CookieManager. */
@Composable
private fun LoginDialog(site: Site, onDismiss: () -> Unit, onSaved: (List<Cookie>) -> Unit) {
    val context = LocalContext.current
    val webView = remember {
        // чистая сессия: так можно добавить второй аккаунт
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // убираем метку встроенного браузера — так больше сайтов разрешают вход
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = WebViewClient()
            loadUrl(site.loginUrl)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().removeAllCookies(null)
            webView.destroy()
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
                    IconButton(onClick = { if (webView.canGoBack()) webView.goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    Text(stringResource(R.string.login_title, site.name), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    FilledTonalButton(onClick = {
                        CookieManager.getInstance().flush()
                        val cookies = collectCookies(site.cookieUrls + listOfNotNull(webView.url))
                        if (cookies.isEmpty()) {
                            Toast.makeText(context, context.getString(R.string.no_cookies_yet), Toast.LENGTH_SHORT).show()
                        } else {
                            onSaved(cookies)
                        }
                    }) { Text(stringResource(R.string.save_session)) }
                }
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
