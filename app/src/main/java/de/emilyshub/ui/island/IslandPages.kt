package de.emilyshub.ui.island

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.room.RoomDatabase
import de.emilyshub.island.Island
import de.emilyshub.island.IslandSaver
import de.emilyshub.island.IslandWrites

/** What [load] reads from [island], read anew on each change there ([Island.changes]) and of [keys]; null until read, or once locked. */
@Composable
internal fun <T> rememberIslandLoad(island: Island<*>, vararg keys: Any?, load: suspend () -> T?): T? {
    var value by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(island.changes, *keys) { value = load() }
    return value
}

/**
 * Saves what the page showing it under [key] has not saved yet: the write [pending] gives, if any, before [island]
 * locks and as the page goes away, by back, by "Fertig" or as the screen turns ([writes]). Never into the saved state.
 * [pending] is asked only then, so that it sees what was set in the same moment.
 */
@Composable
internal fun <D : RoomDatabase> KeepIslandDraft(island: Island<D>, key: Any, writes: IslandWrites<D>, pending: () -> (suspend (D) -> Unit)?) {
    val current by rememberUpdatedState(pending)
    DisposableEffect(key) {
        val saver = island.addSaver(IslandSaver { current() })
        onDispose {
            saver.close()
            current()?.let(writes::later)
        }
    }
}
