package de.emilyshub.ui.tags

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import de.emilyshub.R
import de.emilyshub.tags.Tag
import de.emilyshub.tags.tagNameProblem
import de.emilyshub.ui.fields.NameDialog

/** Asks for a tag's name, starting out with [initial]; one with a problem, also next to the [others], cannot be confirmed. */
@Composable
fun TagNameDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    initial: String,
    others: List<Tag>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) = NameDialog(
    title = title,
    confirm = confirm,
    label = R.string.tag_name,
    taken = R.string.tag_name_taken,
    max = Tag.NAME_MAX,
    initial = initial,
    problemOf = { tagNameProblem(it, others) },
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)
