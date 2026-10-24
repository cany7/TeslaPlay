// SPDX-License-Identifier: AGPL-3.0-only
package io.github.cany7.teslaplay.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cany7.teslaplay.host.R
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun SettingsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = Color.White, onPrimary = Color.Black,
                primaryContainer = Color(0xff333333), onPrimaryContainer = Color.White,
                secondary = Color(0xffdddddd), onSecondary = Color.Black,
                secondaryContainer = Color(0xff333333), onSecondaryContainer = Color.White,
                tertiary = Color(0xffcccccc), onTertiary = Color.Black,
                tertiaryContainer = Color(0xff333333), onTertiaryContainer = Color.White,
                background = Color(0xff111111), onBackground = Color.White,
                surface = Color(0xff111111), onSurface = Color(0xffeeeeee),
                surfaceVariant = Color(0xff333333), onSurfaceVariant = Color(0xffbbbbbb),
                surfaceContainer = Color(0xff222222), surfaceContainerLow = Color(0xff191919),
                surfaceContainerHigh = Color(0xff292929), surfaceContainerHighest = Color(0xff333333),
                surfaceContainerLowest = Color.Black, surfaceTint = Color.White,
                outline = Color(0xff888888), outlineVariant = Color(0xff444444),
                error = Color.White, onError = Color.Black,
                errorContainer = Color(0xff333333), onErrorContainer = Color.White,
                inverseSurface = Color(0xffeeeeee), inverseOnSurface = Color.Black,
                inversePrimary = Color.Black,
            ),
        content = content,
    )
}

@Composable
internal fun SettingsRoute(
    model: SettingsViewModel,
    actions: SettingsActions,
    dialogs: SettingsDialogHost,
    onScroll: (Int) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    SettingsTheme {
        SettingsScreen(state, actions, model::open, model::scrollPosition) { category, offset ->
            model.rememberScroll(category, offset)
            onScroll(offset)
        }
        SettingsPlatformDialog(dialogs)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsState,
    actions: SettingsActions,
    onCategory: (SettingsCategory?) -> Unit,
    initialScroll: (SettingsCategory?) -> Int,
    onScroll: (SettingsCategory?, Int) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchTarget by remember { mutableStateOf<SettingsSearchEntry?>(null) }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val results = remember(searchQuery, context.resources.configuration) { SettingsSearchIndex.search(context, searchQuery) }
    BackHandler(enabled = searchQuery.isNotEmpty()) { searchQuery = "" }
    CompositionLocalProvider(LocalSettingsSearchTarget provides searchTarget?.tag) {
    Surface(Modifier.fillMaxSize()) {
        BoxWithConstraints {
            val gutter = maxOf(if (maxWidth < 360.dp) 16.dp else 24.dp, (maxWidth - 760.dp) / 2)
            key(state.category) {
                val category = state.category
                val scroll = rememberScrollState(initialScroll(category))
                LaunchedEffect(scroll) {
                    snapshotFlow { scroll.value }
                        .distinctUntilChanged()
                        .collect { onScroll(category, it) }
                }
                DisposableEffect(scroll) { onDispose { onScroll(category, scroll.value) } }
                Column(Modifier.fillMaxSize()) {
                    if (category != null)
                        TopAppBar(
                            title = { Text(settingsString(category.title)) },
                            navigationIcon = {
                                IconButton(
                                    onClick = { searchQuery = ""; searchTarget = null; onCategory(null) },
                                    modifier = Modifier.testTag("settings:back"),
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_settings_back),
                                        settingsString(R.string.settings_back),
                                    )
                                }
                            },
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; searchTarget = null },
                        label = { Text(settingsString(R.string.tesla_search_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = gutter).testTag("settings:search"),
                        trailingIcon = { if (searchQuery.isNotEmpty()) TextButton(onClick = { searchQuery = "" }) { Text(settingsString(R.string.tesla_search_clear)) } },
                    )
                    Column(
                        Modifier.weight(1f)
                            .fillMaxWidth()
                            .testTag("settings:page")
                            .verticalScroll(scroll)
                            .padding(horizontal = gutter)
                            .padding(top = 24.dp, bottom = 32.dp)
                    ) {
                        if (searchQuery.isNotBlank()) {
                            SettingsSearchResults(results) { entry ->
                                focus.clearFocus()
                                scope.launch {
                                    scroll.scrollTo(0)
                                    onScroll(entry.category, 0)
                                    searchQuery = ""
                                    searchTarget = entry
                                    onCategory(entry.category)
                                }
                            }
                        } else if (category == null) {
                            Text(
                                settingsString(R.string.settings_title),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            SettingsDescription(R.string.settings_index_summary)
                            SettingsIndexScreen(state, actions, onCategory)
                        } else {
                            searchTarget?.takeIf { it.category == category && it.condition != 0 }?.let {
                                Text(settingsString(it.title), style = MaterialTheme.typography.titleMedium)
                                SettingsDescription(it.condition)
                            }
                            when (category) {
                                SettingsCategory.CONNECTION ->
                                    ConnectionSettingsScreen(state, actions)
                                SettingsCategory.DISPLAY -> DisplaySettingsScreen(state, actions)
                                SettingsCategory.AUDIO -> AudioSettingsScreen(state, actions)
                                SettingsCategory.VEHICLE -> VehicleSettingsScreen(state, actions)
                                SettingsCategory.BROWSER -> BrowserSettingsScreen(state, actions)
                                SettingsCategory.WEB -> WebSettingsScreen(state, actions)
                                SettingsCategory.HELP -> HelpSettingsScreen(state, actions)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SettingsIndexScreen(
    state: SettingsState,
    actions: SettingsActions,
    onCategory: (SettingsCategory?) -> Unit,
) {
    SettingsCategory.entries.forEach { category ->
        Row(
            Modifier.fillMaxWidth()
                .testTag("category:${category.name}")
                .clickable(role = Role.Button) { onCategory(category) }
                .heightIn(min = 80.dp)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(settingsString(category.title), style = MaterialTheme.typography.titleMedium)
                Text(
                    settingsString(category.summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painterResource(R.drawable.ic_settings_chevron),
                null,
                Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "Settings", locale = "zh", widthDp = 360, heightDp = 800)
@Preview(
    name = "Settings large text",
    locale = "zh",
    widthDp = 320,
    heightDp = 640,
    fontScale = 1.3f,
)
@Composable
private fun SettingsIndexPreview() {
    SettingsTheme { SettingsScreen(SettingsState(), previewActions, {}, { 0 }, { _, _ -> }) }
}

@Preview(name = "Audio", locale = "zh", widthDp = 360, heightDp = 800)
@Composable
private fun AudioSettingsPreview() {
    SettingsTheme {
        SettingsScreen(
            SettingsState(
                category = SettingsCategory.AUDIO,
                choices = ChoiceSetting.entries.associateWith { 0 },
                toggles = ToggleSetting.entries.associateWith { false },
            ),
            previewActions,
            {},
            { 0 },
            { _, _ -> },
        )
    }
}

private val previewActions =
    SettingsActions(
        { _, _ -> },
        { _, _ -> },
        { _, _ -> null },
        { _, _ -> null },
        {},
        {},
        {},
        { _, _ -> },
        {},
    )
