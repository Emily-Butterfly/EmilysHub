package de.emilyshub.ui.warum

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.warum.ReasonFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A font brought along for Mein Warum: its [font], the [name] it is published under, and its licence in the app's assets. */
private class BroughtFont(val font: ReasonFont, val name: String, val license: String)

/** Every font but Standard, with its licence (SPEC.md §18): all under the SIL Open Font License 1.1. */
private val FONTS = listOf(
    BroughtFont(ReasonFont.Serif, "Libre Baskerville", "librebaskerville"),
    BroughtFont(ReasonFont.Round, "Varela Round", "varelaround"),
    BroughtFont(ReasonFont.Hand, "Patrick Hand", "patrickhand"),
    BroughtFont(ReasonFont.Script, "Great Vibes", "greatvibes"),
    BroughtFont(ReasonFont.Poster, "Anton", "anton"),
    BroughtFont(ReasonFont.Elegant, "Cormorant Garamond", "cormorantgaramond"),
    BroughtFont(ReasonFont.Typewriter, "Courier Prime", "courierprime"),
)

/** The licences of the fonts Mein Warum brings along (SPEC.md §18), each under its name and what it is called in the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontLicensesScreen(onBack: () -> Unit) {
    val assets = LocalContext.current.assets
    val texts by produceState<List<String>?>(null) {
        value = withContext(Dispatchers.IO) {
            FONTS.map { font -> assets.open("font-licenses/${font.license}.txt").bufferedReader().use { it.readText() } }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.font_licenses), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        val shown = texts ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(FONTS.zip(shown)) { (font, text) ->
                Text(
                    "${font.name} (${stringResource(nameOf(font.font))})",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = familyOf(font.font)),
                    modifier = Modifier.padding(16.dp),
                )
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                HorizontalDivider(Modifier.padding(top = 16.dp))
            }
        }
    }
}
