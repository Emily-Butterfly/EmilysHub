package de.emilyshub.ui.languages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.data.Saving
import de.emilyshub.languages.Language
import de.emilyshub.languages.LanguageItem
import de.emilyshub.languages.Languages
import de.emilyshub.languages.Teacher
import de.emilyshub.languages.TeacherDraft
import de.emilyshub.languages.TeacherEditing
import de.emilyshub.languages.TeacherProblem
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.differsAsSaved
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.Problem
import de.emilyshub.ui.fields.SaveBar
import de.emilyshub.ui.fields.rememberFirstField
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.plusFabSpace
import de.emilyshub.ui.rememberClosing
import de.emilyshub.ui.rememberLeaving
import java.io.Serializable

/**
 * The languages (SPEC.md §15), from the menu of Sprachen: "+" adds one, tapping one renames it, its menu opens its
 * lessons ([onOpen]), also of one archived, archives it or takes it back, and deletes one nothing hangs on, after asking;
 * those archived after the others.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageListScreen(languages: Languages, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val all by remember(languages) { languages.languages() }.collectAsState(initial = null)
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    ManageScaffold(R.string.languages_manage, R.string.language_create, onCreate = { creating = true }, onBack = onBack) { padding ->
        val list = all
        when {
            list == null -> Unit
            list.isEmpty() -> PlaceholderScreen(stringResource(R.string.languages_list_empty), padding)
            else -> LazyColumn(contentPadding = padding.plusFabSpace()) {
                items(list, key = { it.language.id }) { item ->
                    val language = item.language
                    ManagedRow(
                        name = language.name,
                        details = listOfNotNull(
                            stringResource(R.string.languages_archived).takeIf { language.archived },
                            stringResource(R.string.language_kept).takeIf { !item.deletable },
                        ).joinToString(" · ").ifEmpty { null },
                        archived = language.archived,
                        deletable = item.deletable,
                        onClick = { renaming = language.id },
                        onOpen = { onOpen(language.id) },
                        onArchive = { archived -> scope.launchNow { languages.setLanguageArchived(language.id, archived) } },
                        onDelete = { deleting = language.id },
                    )
                }
            }
        }
    }

    val list = all.orEmpty()
    if (creating) {
        val taken = list.map { it.language.name }
        NameDialog(
            title = R.string.language_create,
            confirm = R.string.language_create_confirm,
            initial = "",
            max = Language.NAME_MAX,
            problemOf = { languageNameProblem(it, taken) },
            onConfirm = { name ->
                creating = false
                scope.launchNow { languages.createLanguage(name) }
            },
            onDismiss = { creating = false },
        )
    }
    list.find { it.language.id == renaming }?.let { item ->
        val taken = list.filter { it != item }.map { it.language.name }
        NameDialog(
            title = R.string.language_rename,
            confirm = R.string.language_rename_confirm,
            initial = item.language.name,
            max = Language.NAME_MAX,
            problemOf = { languageNameProblem(it, taken) },
            onConfirm = { name ->
                renaming = null
                scope.launchNow { languages.renameLanguage(item.language.id, name) }
            },
            onDismiss = { renaming = null },
        )
    }
    list.find { it.language.id == deleting }?.let { item ->
        DeleteDialog(
            title = R.string.language_delete_title,
            text = stringResource(R.string.language_delete_text, item.language.name),
            onConfirm = {
                deleting = null
                scope.launchNow { languages.deleteLanguage(item.language.id) }
            },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * The teachers (SPEC.md §15), from the menu of Sprachen: each with the languages they teach; "+" adds one and tapping one
 * opens their editor ([onOpen]); their menu archives them or takes them back, and deletes one without lessons, after
 * asking; those archived after the others.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherListScreen(languages: Languages, onOpen: (TeacherEditorTarget) -> Unit, onBack: () -> Unit) {
    val all by remember(languages) { languages.teachers() }.collectAsState(initial = null)
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    ManageScaffold(R.string.teachers_manage, R.string.teacher_create, onCreate = { onOpen(TeacherEditorTarget.New) }, onBack = onBack) { padding ->
        val list = all
        when {
            list == null -> Unit
            list.isEmpty() -> PlaceholderScreen(stringResource(R.string.teachers_empty), padding)
            else -> LazyColumn(contentPadding = padding.plusFabSpace()) {
                items(list, key = { it.teacher.id }) { item ->
                    val teacher = item.teacher
                    ManagedRow(
                        name = teacher.name,
                        details = listOfNotNull(
                            stringResource(R.string.languages_archived).takeIf { teacher.archived },
                            item.languages.joinToString(", ") { it.name }.ifEmpty { null },
                        ).joinToString(" · ").ifEmpty { null },
                        archived = teacher.archived,
                        deletable = !item.hasLessons,
                        onClick = { onOpen(TeacherEditorTarget.Edit(teacher.id)) },
                        onArchive = { archived -> scope.launchNow { languages.setTeacherArchived(teacher.id, archived) } },
                        onDelete = { deleting = teacher.id },
                    )
                }
            }
        }
    }

    all.orEmpty().find { it.teacher.id == deleting }?.let { item ->
        DeleteDialog(
            title = R.string.teacher_delete_title,
            text = stringResource(R.string.teacher_delete_text, item.teacher.name),
            onConfirm = {
                deleting = null
                scope.launchNow { languages.deleteTeacher(item.teacher.id) }
            },
            onDismiss = { deleting = null },
        )
    }
}

/** What the editor of teachers opens: a new one, or one saved already. */
sealed interface TeacherEditorTarget : Serializable {
    data object New : TeacherEditorTarget

    data class Edit(val teacherId: Long) : TeacherEditorTarget
}

/**
 * A teacher (SPEC.md §15), new or saved: their name, a note, and the languages they teach, at least one, of those not
 * archived and those they teach already. A language taken away leaves their lessons of it as they are.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeacherEditorScreen(languages: Languages, target: TeacherEditorTarget, onClose: () -> Unit) {
    val teacherId = (target as? TeacherEditorTarget.Edit)?.teacherId
    var editing by rememberSaveable { mutableStateOf(if (teacherId == null) TeacherEditing.New else null) }
    var draft by rememberSaveable { mutableStateOf(TeacherDraft()) }
    // The form as a new one starts out, to tell whether anything was typed.
    val fresh = rememberSaveable { draft }
    var showProblems by rememberSaveable { mutableStateOf(false) }
    val all by remember(languages) { languages.languages() }.collectAsState(initial = emptyList())

    val opened = editing
    if (opened == null) {
        LaunchedEffect(teacherId) {
            val saved = languages.teacherEditing(checkNotNull(teacherId))
            if (saved == null) {
                onClose()
            } else {
                draft = checkNotNull(saved.original)
                editing = saved
            }
        }
        return
    }
    val problems = draft.problems
    val closeWith = rememberClosing(onClose)
    val firstField = rememberFirstField(new = teacherId == null)
    val save = {
        if (problems.isNotEmpty()) {
            showProblems = true
            firstField.saveFailed(atField = draft.nameProblem != null)
        } else {
            val saved = draft
            closeWith.leaveThrough {
                when (languages.saveTeacher(opened, saved)) {
                    is Saving.Saved -> onClose()
                    // A language deleted or archived meanwhile left none: it is taken from the draft, which says so.
                    is Saving.Refused -> {
                        draft = draft.copy(languageIds = draft.languageIds intersect offeredIds(all, opened))
                        showProblems = true
                        closeWith.reopen()
                    }
                }
            }
        }
    }
    val leave = rememberLeaving(
        closing = closeWith,
        changed = { differsAsSaved(draft, opened.original ?: fresh, TeacherDraft::cleaned) },
        onSave = save,
        onDiscard = onClose,
    )
    val offered = all.map { it.language }.filter { !it.archived || it.id in opened.original?.languageIds.orEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (teacherId == null) R.string.teacher_new else R.string.teacher_edit),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        bottomBar = { SaveBar(onSave = save) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val nameMissing = showProblems && TeacherProblem.NameMissing in problems
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.teacher_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                isError = nameMissing || TeacherProblem.NameTooLong in problems,
                supportingText = {
                    if (nameMissing) {
                        Text(stringResource(R.string.teacher_name_missing))
                    } else {
                        LengthHint(InputText.lengthOf(draft.cleanedName), Teacher.NAME_MAX)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstField.requester),
            )
            OutlinedTextField(
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it) },
                label = { Text(stringResource(R.string.teacher_note)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = TeacherProblem.NoteTooLong in problems,
                supportingText = { LengthHint(InputText.lengthOf(draft.cleanedNote), Teacher.NOTE_MAX) },
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.teacher_languages), style = MaterialTheme.typography.titleSmall)
                if (offered.isEmpty()) Text(stringResource(R.string.teacher_languages_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    offered.forEach { language ->
                        val chosen = language.id in draft.languageIds
                        ChoiceChip(language.name, chosen) {
                            draft = draft.copy(languageIds = if (chosen) draft.languageIds - language.id else draft.languageIds + language.id)
                        }
                    }
                }
                if (showProblems && TeacherProblem.LanguagesMissing in problems) Problem(stringResource(R.string.teacher_languages_missing))
            }
        }
    }
}

/** The languages a teacher being [editing] may teach: those not archived, and those they taught already. */
private fun offeredIds(all: List<LanguageItem>, editing: TeacherEditing): Set<Long> =
    all.map { it.language }.filter { !it.archived || it.id in editing.original?.languageIds.orEmpty() }.map { it.id }.toSet()

/** A screen of things to manage, by its [title], with "+" to add one ([create] its label). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageScaffold(
    title: Int,
    create: Int,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) { Icon(Icons.Filled.Add, contentDescription = stringResource(create)) }
        },
    ) { content(it) }
}

/**
 * A language or teacher in its list: tapped, it opens; its menu opens its lessons if it has [onOpen], archives it or takes
 * it back, and deletes it if [deletable].
 */
@Composable
private fun ManagedRow(
    name: String,
    details: String?,
    archived: Boolean,
    deletable: Boolean,
    onClick: () -> Unit,
    onOpen: (() -> Unit)? = null,
    onArchive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = details?.let { { Text(it) } },
        trailingContent = {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.languages_entry_menu, name))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    onOpen?.let { open ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.language_open)) },
                            onClick = {
                                expanded = false
                                open()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(if (archived) R.string.languages_unarchive else R.string.languages_archive)) },
                        onClick = {
                            expanded = false
                            onArchive(!archived)
                        },
                    )
                    if (deletable) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            onClick = {
                                expanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

/** Asks before deleting: [title] and [text], and "Löschen". */
@Composable
private fun DeleteDialog(title: Int, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
