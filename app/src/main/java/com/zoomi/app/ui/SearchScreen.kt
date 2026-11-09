package com.zoomi.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zoomi.app.R
import com.zoomi.app.SearchUi
import com.zoomi.core.places.PlaceKind
import com.zoomi.core.places.SavedPlace
import com.zoomi.core.search.Place
import com.zoomi.core.search.SearchQueryPolicy

@Composable
fun SearchScreen(
    search: SearchUi,
    saved: List<SavedPlace>,
    onQuery: (String) -> Unit,
    onPick: (Place) -> Unit,
    onPickSaved: (SavedPlace) -> Unit,
    onDeleteSaved: (SavedPlace) -> Unit,
    onBack: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back") }
                OutlinedTextField(
                    value = search.query,
                    onValueChange = onQuery,
                    placeholder = { Text("Where to?") },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    trailingIcon = {
                        if (search.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else if (search.query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("Clear") }
                    },
                )
            }

            LazyColumn(Modifier.fillMaxSize()) {
                val showSaved = SearchQueryPolicy.normalize(search.query) == null
                if (showSaved) {
                    if (saved.isEmpty()) {
                        item {
                            Hint("Type at least ${SearchQueryPolicy.MIN_CHARS} letters to search. " +
                                "You can also long-press anywhere on the map to go there. " +
                                "Save Home, Work and favourites from a destination's card.")
                        }
                    } else {
                        item { SectionTitle("Saved places") }
                        items(saved, key = { it.id }) { p ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPickSaved(p) }.padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        when (p.kind) { PlaceKind.HOME -> "🏠 "; PlaceKind.WORK -> "💼 "; PlaceKind.FAVORITE -> "★ " } + p.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (p.address.isNotBlank()) Text(p.address, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                TextButton(onClick = { onDeleteSaved(p) }) { Text("Remove") }
                            }
                        }
                    }
                } else {
                    search.error?.let { item { Hint(it) } }
                    items(search.results) { r ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(r) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Text(r.title, style = MaterialTheme.typography.bodyLarge)
                            if (r.subtitle.isNotBlank()) Text(r.subtitle, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider()
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Search by Photon (komoot) · data © OpenStreetMap contributors",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) = Text(
    t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
)

@Composable
private fun Hint(t: String) = Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
