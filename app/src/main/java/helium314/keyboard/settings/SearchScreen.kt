// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.settings.preferences.PreferenceCategory

import helium314.keyboard.latin.utils.prefs

@Composable
fun SearchSettingsScreen(
    onClickBack: () -> Unit,
    title: String,
    settings: List<Any?>,
    searchBar: Boolean = false,
    content: @Composable (ColumnScope.() -> Unit)? = null // overrides settings if not null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val customCount = context.prefs().getInt("custom_layouts_count", 0)
    SearchScreen(
        onClickBack = onClickBack,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        },
        content = {
            if (content != null) content()
            else {
                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ) { innerPadding ->
                    // Calculate groups only when settings change
                    val groups = remember(settings) { groupSettings(settings) }

                    LazyColumn(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                    ) {
                        items(groups, key = { (titleRes, _) -> titleRes ?: 0 }) { (titleRes, keys) ->
                            SettingsGroupCard(titleRes, keys)
                        }
                    }
                }
            }
        },
        filteredItems = { 
            SettingsActivity.settingsContainer.filter(it).filter { setting ->
                val key = setting.key
                if (key.startsWith(helium314.keyboard.latin.settings.Settings.PREF_LAYOUT_PREFIX + "CUSTOM")) {
                    val index = key.removePrefix(helium314.keyboard.latin.settings.Settings.PREF_LAYOUT_PREFIX + "CUSTOM").toIntOrNull() ?: 0
                    if (index > customCount) return@filter false
                }
                if (key == "add_custom_layout") return@filter false
                when (helium314.keyboard.latin.BuildConfig.FLAVOR) {
                    "offlinelite" -> {
                        !key.startsWith("gemini") &&
                        !key.startsWith("groq") &&
                        !key.startsWith("huggingface") &&
                        !key.startsWith("ai_provider") &&
                        !key.startsWith("offline_model_path")
                    }
                    "offline" -> {
                        !key.startsWith("gemini") &&
                        !key.startsWith("groq") &&
                        !key.startsWith("huggingface") &&
                        !key.startsWith("ai_provider")
                    }
                    else -> true
                }
            } 
        },
        itemContent = { it.Preference() },
        searchBar = searchBar,
    )
}

/**
 * One titled group of settings in a rounded card. A group titled [R.string.settings_category_more]
 * starts collapsed behind a "More" row, which is where a page keeps what is rarely changed.
 */
@Composable
fun SettingsGroupCard(titleRes: Int?, keys: List<String>) {
    val isMore = titleRes == helium314.keyboard.latin.R.string.settings_category_more
    var expanded by rememberSaveable(titleRes) { mutableStateOf(!isMore) }
    if (titleRes != null && !isMore) {
        PreferenceCategory(stringResource(titleRes), icon = categoryIcon(titleRes))
    }
    androidx.compose.material3.Card(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            if (isMore) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        stringResource(titleRes!!),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (expanded) "" else keys.size.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Icon(
                        painterResource(helium314.keyboard.latin.R.drawable.ic_arrow_left),
                        null,
                        Modifier.rotate(if (expanded) 90f else -90f)
                    )
                }
            }
            if (expanded) keys.forEachIndexed { index, key ->
                androidx.compose.runtime.key(key) {
                    // hairline between rows inside a card, as in the
                    // grouped lists this UI is modelled on
                    if (index > 0 || isMore) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    SettingsActivity.settingsContainer[key]?.Preference()
                }
            }
        }
    }
}

/** Groups a flat settings list: an Int starts a new titled group, a String is a setting key. */
fun groupSettings(settings: List<Any?>): List<Pair<Int?, List<String>>> {
    val result = mutableListOf<Pair<Int?, MutableList<String>>>()
    var currentGroup = mutableListOf<String>()
    result.add(null to currentGroup)
    settings.forEach { item ->
        if (item is Int) {
            currentGroup = mutableListOf()
            result.add(item to currentGroup)
        } else if (item is String) {
            currentGroup.add(item)
        }
    }
    return result.filter { it.second.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T: Any?> SearchScreen(
    onClickBack: () -> Unit,
    title: @Composable () -> Unit,
    filteredItems: (String) -> List<T>,
    itemContent: @Composable (T) -> Unit,
    icon: @Composable (() -> Unit)? = null,
    menu: List<Pair<String, () -> Unit>>? = null,
    headerContent: (@Composable () -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
    searchBar: Boolean = false,
) {
    // searchText and showSearch should have the same remember or rememberSaveable
    // saveable survives orientation changes and switching between screens, but shows the
    // keyboard in unexpected situations such as going back from another screen, which is rather annoying
    var searchText by remember { mutableStateOf(TextFieldValue()) }
    var showSearch by remember { mutableStateOf(false) }
    Scaffold(contentWindowInsets = WindowInsets(0))
    { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {

            fun setShowSearch(value: Boolean) {
                showSearch = value
                if (!value) searchText = TextFieldValue()
            }
            BackHandler {
                if (showSearch || searchText.text.isNotEmpty()) setShowSearch(false)
                else onClickBack()
            }
            Surface(
                color = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                Column {
                    TopAppBar(
                        title = title,
                        windowInsets = WindowInsets(0),
                        navigationIcon = {
                            BackButton {
                                if (showSearch) setShowSearch(false)
                                else onClickBack()
                            }
                        },
                        actions = {
                            if (icon == null)
                                IconButton(onClick = { setShowSearch(!showSearch) }) { SearchIcon() }
                            else
                                icon()
                            if (menu != null)
                                Box {
                                    var showMenu by remember { mutableStateOf(false) }
                                    IconButton(
                                        onClick = { showMenu = true }
                                    ) { Icon(painterResource(R.drawable.ic_arrow_left), "menu", Modifier.rotate(-90f)) }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        menu.forEach {
                                            DropdownMenuItem(
                                                text = { Text(it.first) },
                                                onClick = { showMenu = false; it.second() }
                                            )
                                        }
                                    }
                                }
                        },
                    )
                    if (searchBar && !showSearch) {
                        // an always-visible entry point, so search is the first thing on the page
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .clickable { setShowSearch(true) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                SearchIcon()
                                Text(
                                    stringResource(helium314.keyboard.latin.R.string.settings_search_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                    ExpandableSearchField(
                        expanded = showSearch,
                        onDismiss = { setShowSearch(false) },
                        search = searchText,
                        onSearchChange = { searchText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                if (searchText.text.isBlank() && content != null) {
                    Column {
                        content()
                    }
                } else {
                    val items = remember(searchText.text, filteredItems) { filteredItems(searchText.text) }
                    Scaffold(
                        contentWindowInsets = WindowInsets(0)
                    ) { innerPadding ->
                        LazyColumn(contentPadding = innerPadding) {
                            if (searchText.text.isBlank() && headerContent != null) {
                                item {
                                    headerContent()
                                }
                            }
                            items(items) {
                                itemContent(it)
                            }
                        }
                    }
                }
            }
        }
    }
}

// from StreetComplete
/** Expandable text field that can be dismissed and requests focus when it is expanded */
@Composable
fun ExpandableSearchField(
    expanded: Boolean,
    onDismiss: () -> Unit,
    search: TextFieldValue,
    onSearchChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (expanded) focusRequester.requestFocus()
    }
    AnimatedVisibility(visible = expanded, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = modifier
                .focusRequester(focusRequester)
                .clip(androidx.compose.foundation.shape.CircleShape),
            trailingIcon = { IconButton(onClick = {
                if (search.text.isBlank()) onDismiss()
                else onSearchChange(TextFieldValue())
            }) { CloseIcon(android.R.string.cancel) } },
            singleLine = true,
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = colors,
            textStyle = contentTextDirectionStyle,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
    }
}


/** Monotone icons for settings category headers, for faster visual scanning. */
fun categoryIcon(titleRes: Int): Int? = when (titleRes) {
    helium314.keyboard.latin.R.string.settings_category_input -> helium314.keyboard.latin.R.drawable.ic_settings_preferences
    helium314.keyboard.latin.R.string.settings_category_additional_keys -> helium314.keyboard.latin.R.drawable.ic_settings_toolbar
    helium314.keyboard.latin.R.string.settings_category_clipboard_history -> helium314.keyboard.latin.R.drawable.ic_clipboard_pin_rounded
    helium314.keyboard.latin.R.string.settings_category_correction -> helium314.keyboard.latin.R.drawable.ic_settings_correction
    helium314.keyboard.latin.R.string.settings_category_suggestions -> helium314.keyboard.latin.R.drawable.ic_autocorrect_rounded
    helium314.keyboard.latin.R.string.settings_category_space -> helium314.keyboard.latin.R.drawable.ic_arrow_horizontal_rounded
    helium314.keyboard.latin.R.string.settings_category_experimental -> helium314.keyboard.latin.R.drawable.ic_settings_advanced
    helium314.keyboard.latin.R.string.settings_category_miscellaneous -> helium314.keyboard.latin.R.drawable.ic_settings_default
    else -> null
}
