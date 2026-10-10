// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.provider.UserDictionary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.personalization.LearnedWord
import helium314.keyboard.latin.personalization.PersonalDictCandidates
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class LearnedSort { MOST_USED, ALPHABETICAL }

/**
 * What the keyboard learned from typing, and the words waiting to go into the personal dictionary.
 * Reads the live dictionaries of the running keyboard, so it needs the keyboard to have been opened.
 */
@Composable
fun LearnedWordsScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var learned by remember { mutableStateOf<List<LearnedWord>?>(null) }
    var pending by remember { mutableStateOf(emptyList<PersonalDictCandidates.Entry>()) }
    var available by remember { mutableStateOf(true) }
    var showAll by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(LearnedSort.MOST_USED) }
    var selected by remember { mutableStateOf<LearnedWord?>(null) }
    var confirmForgetAll by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        withContext(Dispatchers.IO) {
            pending = PersonalDictCandidates.pending(ctx)
            val facilitator = KeyboardSwitcher.getInstance().getLatinIME()?.getDictionaryFacilitator()
            available = facilitator != null
            learned = try { facilitator?.learnedWords ?: emptyList() } catch (e: Exception) {
                Log.e("LearnedWords", "could not read learned words", e)
                emptyList()
            }
        }
    }

    fun visibleWords(term: String): List<LearnedWord> {
        val all = learned ?: return emptyList()
        val shown = all.filter { (showAll || !it.inDictionary) && it.word.contains(term, ignoreCase = true) }
        return when (sort) {
            LearnedSort.MOST_USED -> shown.sortedByDescending { it.frequency }
            LearnedSort.ALPHABETICAL -> shown.sortedBy { it.word.lowercase(it.locale) }
        }
    }

    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.learned_words)) },
        menu = listOf(
            stringResource(if (showAll) R.string.learned_words_show_new else R.string.learned_words_show_all) to { showAll = !showAll },
            stringResource(if (sort == LearnedSort.MOST_USED) R.string.sort_alphabetical else R.string.sort_most_used) to {
                sort = if (sort == LearnedSort.MOST_USED) LearnedSort.ALPHABETICAL else LearnedSort.MOST_USED
            },
            stringResource(R.string.learned_words_clear_dismissed) to {
                scope.launch(Dispatchers.IO) { PersonalDictCandidates.clearDismissed(ctx) }
                Unit
            },
            stringResource(R.string.learned_words_forget_all) to { confirmForgetAll = true },
        ),
        headerContent = {
            Column {
                if (pending.isNotEmpty()) {
                    LearnedSectionLabel(stringResource(R.string.learned_words_pending))
                    pending.forEach { entry ->
                        PendingRow(
                            entry = entry,
                            onApprove = {
                                scope.launch(Dispatchers.IO) {
                                    PersonalDictCandidates.approve(ctx, entry.word, entry.locale)
                                    addToPersonalDictionary(ctx, entry.word, entry.locale)
                                    refresh++
                                }
                            },
                            onDismiss = {
                                scope.launch(Dispatchers.IO) {
                                    PersonalDictCandidates.reject(ctx, entry.word, entry.locale)
                                    refresh++
                                }
                            }
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                when {
                    !available -> LearnedHint(stringResource(R.string.learned_words_unavailable))
                    learned == null -> LearnedHint(stringResource(R.string.learned_words_loading))
                    else -> LearnedSectionLabel(stringResource(
                        if (showAll) R.string.learned_words_all_header else R.string.learned_words_new_header
                    ))
                }
            }
        },
        filteredItems = { term -> visibleWords(term) },
        itemContent = { word ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selected = word }
                    .padding(vertical = 8.dp, horizontal = 16.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(word.word, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        word.locale.localizedDisplayName(ctx.resources),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )

    selected?.let { word ->
        LearnedWordDialog(
            word = word,
            onDismissRequest = { selected = null },
            onAdd = {
                selected = null
                scope.launch(Dispatchers.IO) { addToPersonalDictionary(ctx, word.word, word.locale) }
            },
            onForget = {
                selected = null
                scope.launch(Dispatchers.IO) {
                    val ime = KeyboardSwitcher.getInstance().getLatinIME()
                    ime?.getDictionaryFacilitator()?.forgetLearnedWord(word.word, word.locale)
                    withContext(Dispatchers.Main) { ime?.onDictionaryEdited() }
                    learned = learned?.filterNot { it == word }
                }
            },
            onBlock = {
                selected = null
                scope.launch(Dispatchers.Main) {
                    // blocks it from suggestions and removes it from every dictionary it is in
                    KeyboardSwitcher.getInstance().getLatinIME()?.removeSuggestion(word.word)
                    learned = learned?.filterNot { it.word == word.word }
                }
            }
        )
    }

    if (confirmForgetAll) {
        ConfirmationDialog(
            onDismissRequest = { confirmForgetAll = false },
            onConfirmed = {
                confirmForgetAll = false
                scope.launch(Dispatchers.IO) {
                    val ime = KeyboardSwitcher.getInstance().getLatinIME()
                    ime?.getDictionaryFacilitator()?.clearUserHistoryDictionary(ctx)
                    withContext(Dispatchers.Main) { ime?.onDictionaryEdited() }
                    refresh++
                }
            },
            content = { Text(stringResource(R.string.learned_words_forget_all_confirmation)) }
        )
    }
}

@Composable
private fun LearnedSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun LearnedHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
private fun PendingRow(entry: PersonalDictCandidates.Entry, onApprove: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.word, style = MaterialTheme.typography.bodyLarge)
            Text(
                ctx.resources.getQuantityString(R.plurals.learned_words_uses, entry.uses, entry.uses) +
                    "  ·  " + entry.locale.localizedDisplayName(ctx.resources),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.learned_words_dismiss))
        }
        IconButton(onClick = onApprove) {
            Icon(painterResource(R.drawable.ic_plus), stringResource(R.string.learned_words_add))
        }
    }
}

@Composable
private fun LearnedWordDialog(
    word: LearnedWord,
    onDismissRequest: () -> Unit,
    onAdd: () -> Unit,
    onForget: () -> Unit,
    onBlock: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text(
                    word.word,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                LearnedDialogAction(stringResource(R.string.learned_words_add), stringResource(R.string.learned_words_add_summary), onAdd)
                LearnedDialogAction(stringResource(R.string.learned_words_forget), stringResource(R.string.learned_words_forget_summary), onForget)
                LearnedDialogAction(stringResource(R.string.learned_words_block), stringResource(R.string.learned_words_block_summary), onBlock)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
private fun LearnedDialogAction(title: String, summary: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Adds a word to the system personal dictionary, as the keyboard's own automatic adding does. */
private fun addToPersonalDictionary(context: Context, word: String, locale: Locale) {
    runCatching { UserDictionary.Words.addWord(context, word, 250, null, locale) }
        .onFailure { Log.e("LearnedWords", "could not add $word", it) }
}
