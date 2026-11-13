package de.emilyshub.ui.languages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.data.NameProblem
import de.emilyshub.data.Saving
import de.emilyshub.languages.AttachmentRef
import de.emilyshub.languages.Language
import de.emilyshub.languages.Languages
import de.emilyshub.languages.LessonChoices
import de.emilyshub.languages.LessonDraft
import de.emilyshub.languages.LessonEditing
import de.emilyshub.languages.LessonFiles
import de.emilyshub.languages.LessonProblem
import de.emilyshub.languages.Teacher
import de.emilyshub.languages.TeacherDraft
import de.emilyshub.languages.TeacherEditing
import de.emilyshub.languages.TeacherProblem
import de.emilyshub.languages.Topic
import de.emilyshub.ledger.Money
import de.emilyshub.notes.Markdown
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.differsAsSaved
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.DateValue
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.Problem
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.notes.MarkdownText
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.rememberClosing
import de.emilyshub.ui.rememberLeaving
import java.io.Serializable

/** What the editor of lessons opens: a new one, of the language [languageId] if given, or one saved already. */
sealed interface LessonEditorTarget : Serializable {
    data class New(val languageId: Long? = null) : LessonEditorTarget

    data class Edit(val lessonId: Long) : LessonEditorTarget
}

/**
 * A lesson held (SPEC.md §15), new or saved: its date, today for a new one; how long, at a tap or typed; its language and
 * its teacher, of those who teach it, either made on the spot ("Neue Sprache", "Neuer Lehrer", the latter teaching it
 * then) or another teacher given it ("Anderen Lehrer wählen"); the topics treated, chosen from its language's or typed
 * anew, gone with a language changed, which it says; text, photos and PDFs imported ([LessonAttachments]), what was
 * imported let go of when it is left unsaved; a price for the evaluation, and a note in Markdown, with
 * its preview. Loaded and saved through its editing session; refused, it says why at the field. Deleting asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonEditorScreen(
    languages: Languages,
    files: LessonFiles,
    target: LessonEditorTarget,
    onClose: () -> Unit,
    onOpenAttachment: (AttachmentRef) -> Unit,
) {
    val lessonId = (target as? LessonEditorTarget.Edit)?.lessonId
    var editing by rememberSaveable { mutableStateOf(if (lessonId == null) LessonEditing.New else null) }
    var draft by rememberSaveable { mutableStateOf(languages.newLesson((target as? LessonEditorTarget.New)?.languageId)) }
    // The form as a new one starts out, to tell whether anything was typed.
    val fresh = rememberSaveable { draft }
    var showProblems by rememberSaveable { mutableStateOf(false) }
    // What saving was refused for, as the editor alone cannot tell: a language or teacher no longer offered.
    var refused by rememberSaveable { mutableStateOf(emptySet<LessonProblem>()) }
    var previewing by rememberSaveable { mutableStateOf(false) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<EditorDialog?>(null) }
    // Whether the topics went with a language changed, to say so.
    var topicsLost by rememberSaveable { mutableStateOf(false) }
    val choices by remember(languages) { languages.choices() }.collectAsState(initial = LessonChoices(emptyList(), emptyList()))
    val allLanguages by remember(languages) { languages.languages() }.collectAsState(initial = emptyList())
    val allTeachers by remember(languages) { languages.teachers() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val opened = editing
    if (opened == null) {
        LaunchedEffect(lessonId) {
            val saved = languages.editing(checkNotNull(lessonId))
            if (saved == null) {
                onClose()
            } else {
                draft = checkNotNull(saved.original)
                editing = saved
            }
        }
        return
    }

    val problems = draft.problems + refused
    val closeWith = rememberClosing(onClose)
    // Left without saving: what was imported for it is let go of.
    val discard = {
        val left = draft
        scope.launchNow { files.discard(left, opened.original) }
        onClose()
    }
    val save = {
        if (draft.problems.isNotEmpty()) {
            showProblems = true
        } else {
            val saved = draft
            closeWith.leaveThrough {
                when (val saving = languages.save(opened, saved)) {
                    is Saving.Saved -> onClose()
                    is Saving.Refused -> {
                        refused = saving.problems
                        showProblems = true
                    }
                }
            }
        }
    }
    val leave = rememberLeaving(
        closing = closeWith,
        changed = { differsAsSaved(draft, opened.original ?: fresh, LessonDraft::cleaned) },
        onSave = save,
        onDiscard = discard,
    )
    // The language and teacher it was saved with stay to choose, archived or not; the others only while offered.
    val savedDraft = opened.original
    val offeredLanguages = (choices.languages + allLanguages.map { it.language }.filter { it.id == savedDraft?.languageId }).distinctBy { it.id }
    val languageId = draft.languageId
    val offeredTeachers = languageId?.let { id ->
        val teaching = choices.teachers.filter { it.teaches(id) }.map { it.teacher }
        val kept = allTeachers.map { it.teacher }.filter { it.id == savedDraft?.teacherId && id == savedDraft.languageId }
        (teaching + kept).distinctBy { it.id }.sortedWith(compareBy(InputText.byName) { it.name })
    }.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (lessonId == null) R.string.lesson_new else R.string.lesson_edit),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (lessonId != null) {
                        IconButton(onClick = { dialog = EditorDialog.Delete }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    TextButton(onClick = save) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DateValue(stringResource(R.string.lesson_date), draft.date ?: languages.today(), onClick = { choosingDate = true })
            MinutesField(draft.minutes, showProblems && LessonProblem.MinutesInvalid in problems) { draft = draft.copy(minutes = it) }
            LanguageChoice(
                offered = offeredLanguages,
                chosen = draft.languageId,
                problem = when {
                    showProblems && LessonProblem.LanguageMissing in problems -> stringResource(R.string.lesson_language_missing)
                    LessonProblem.LanguageNotOffered in problems -> stringResource(R.string.lesson_language_not_offered)
                    else -> null
                },
                onChoose = { id ->
                    refused = emptySet()
                    // A teacher who does not teach the new one does not fit any more, unless it is the one saved with it.
                    val teacherId = draft.teacherId
                    val keepsTeacher = choices.teachers.any { it.teacher.id == teacherId && it.teaches(id) } ||
                        (teacherId == savedDraft?.teacherId && id == savedDraft?.languageId)
                    if (id != draft.languageId && draft.hasTopics) topicsLost = true
                    draft = draft.withLanguage(id).copy(teacherId = draft.teacherId.takeIf { keepsTeacher })
                },
                onCreate = { dialog = EditorDialog.NewLanguage },
            )
            TeacherChoice(
                languageChosen = languageId != null,
                offered = offeredTeachers,
                chosen = draft.teacherId,
                problem = when {
                    showProblems && LessonProblem.TeacherMissing in problems -> stringResource(R.string.lesson_teacher_missing)
                    LessonProblem.TeacherNotOffered in problems -> stringResource(R.string.lesson_teacher_not_offered)
                    else -> null
                },
                onChoose = {
                    refused = emptySet()
                    draft = draft.copy(teacherId = it)
                },
                onCreate = { dialog = EditorDialog.NewTeacher },
                onChooseOther = { dialog = EditorDialog.OtherTeacher },
            )
            if (languageId != null) {
                val kept = savedDraft?.topicIds.orEmpty().takeIf { savedDraft?.languageId == languageId }.orEmpty()
                TopicsField(
                    offered = choices.topicsOf(languageId, kept),
                    all = choices.topics.filter { it.languageId == languageId },
                    draft = draft,
                    problem = when {
                        LessonProblem.TopicNotOffered in problems -> stringResource(R.string.lesson_topic_not_offered)
                        topicsLost -> stringResource(R.string.lesson_topics_lost)
                        else -> null
                    },
                    onChange = {
                        refused = refused - LessonProblem.TopicNotOffered
                        topicsLost = false
                        draft = it
                    },
                )
            }
            PriceField(draft.price, draft.priceCents, LessonProblem.PriceInvalid in problems) { draft = draft.copy(price = it) }
            NoteField(draft.note, previewing, onPreviewingChange = { previewing = it }, tooLong = LessonProblem.NoteTooLong in problems) {
                draft = draft.copy(note = it)
            }
            LessonAttachments(
                files = files,
                draft = draft,
                tooMany = LessonProblem.TooManyAttachments in problems,
                onChange = { change -> draft = change(draft) },
                onOpen = onOpenAttachment,
            )
        }
    }

    if (choosingDate) {
        DateDialog(
            initial = draft.date ?: languages.today(),
            onConfirm = {
                draft = draft.copy(date = it)
                choosingDate = false
            },
            onDismiss = { choosingDate = false },
        )
    }
    when (dialog) {
        null -> Unit
        EditorDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.lesson_delete_title)) },
            text = { Text(stringResource(R.string.lesson_delete_text, (draft.date ?: languages.today()).numericDate())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val left = draft
                        closeWith {
                            languages.delete(checkNotNull(lessonId))
                            files.discard(left, saved = null)
                        }
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        EditorDialog.NewLanguage -> {
            val taken = allLanguages.map { it.language.name }
            NameDialog(
                title = R.string.lesson_language_new,
                confirm = R.string.language_create_confirm,
                initial = "",
                max = Language.NAME_MAX,
                problemOf = { languageNameProblem(it, taken) },
                onConfirm = { name ->
                    dialog = null
                    scope.launchNow {
                        (languages.createLanguage(name) as? Saving.Saved)?.let {
                            if (draft.hasTopics) topicsLost = true
                            draft = draft.withLanguage(it.id).copy(teacherId = null)
                        }
                    }
                },
                onDismiss = { dialog = null },
            )
        }
        EditorDialog.NewTeacher -> {
            val language = checkNotNull(languageId)
            NameDialog(
                title = R.string.lesson_teacher_new,
                confirm = R.string.teacher_create_confirm,
                initial = "",
                max = Teacher.NAME_MAX,
                problemOf = { teacherNameProblem(it) },
                onConfirm = { name ->
                    dialog = null
                    scope.launchNow {
                        val saving = languages.saveTeacher(TeacherEditing.New, TeacherDraft(name, languageIds = setOf(language)))
                        (saving as? Saving.Saved)?.let { draft = draft.copy(teacherId = it.id) }
                    }
                },
                onDismiss = { dialog = null },
            )
        }
        EditorDialog.OtherTeacher -> {
            val language = checkNotNull(languageId)
            val name = offeredLanguages.find { it.id == language }?.name.orEmpty()
            OtherTeacherDialog(
                languageName = name,
                others = choices.teachers.filterNot { it.teaches(language) }.map { it.teacher },
                onChoose = { teacher ->
                    dialog = null
                    scope.launchNow {
                        languages.teach(teacher.id, language)
                        draft = draft.copy(teacherId = teacher.id)
                    }
                },
                onDismiss = { dialog = null },
            )
        }
    }
}

/** The dialogs the editor opens, one at a time. */
private enum class EditorDialog { Delete, NewLanguage, NewTeacher, OtherTeacher }

/** How long, in minutes: a chip each for the usual lengths, or typed; [invalid] told under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinutesField(minutes: String, invalid: Boolean, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LessonDraft.QUICK_MINUTES.forEach { quick ->
                ChoiceChip(durationText(quick.toLong()), chosen = minutes.trim() == quick.toString()) { onChange(quick.toString()) }
            }
        }
        OutlinedTextField(
            value = minutes,
            onValueChange = { typed -> onChange(typed.filter { it.isDigit() }) },
            label = { Text(stringResource(R.string.lesson_minutes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = invalid,
            supportingText = { if (invalid) Text(stringResource(R.string.lesson_minutes_invalid)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The languages to choose from, a chip each, and the way to make a new one; [problem] told under them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChoice(offered: List<Language>, chosen: Long?, problem: String?, onChoose: (Long) -> Unit, onCreate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.lesson_language), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            offered.forEach { language -> ChoiceChip(language.name, chosen = chosen == language.id) { onChoose(language.id) } }
        }
        OutlinedButton(onClick = onCreate) { Text(stringResource(R.string.lesson_language_new)) }
        problem?.let { Problem(it) }
    }
}

/**
 * The teachers of the language chosen, a chip each, and the ways to make a new one or give another one the language;
 * nothing to choose before a language is ([languageChosen]). [problem] told under them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeacherChoice(
    languageChosen: Boolean,
    offered: List<Teacher>,
    chosen: Long?,
    problem: String?,
    onChoose: (Long) -> Unit,
    onCreate: () -> Unit,
    onChooseOther: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.lesson_teacher), style = MaterialTheme.typography.titleSmall)
        if (!languageChosen) {
            Text(stringResource(R.string.lesson_teacher_first_language), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                offered.forEach { teacher -> ChoiceChip(teacher.name, chosen = chosen == teacher.id) { onChoose(teacher.id) } }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCreate) { Text(stringResource(R.string.lesson_teacher_new)) }
                OutlinedButton(onClick = onChooseOther) { Text(stringResource(R.string.lesson_teacher_other)) }
            }
        }
        problem?.let { Problem(it) }
    }
}

/**
 * The topics treated (SPEC.md §15): those of the language [offered], a chip each to choose, narrowed by what is typed, and
 * those typed anew, which saving puts on the list; one typed that reads as one of [all] is that one, unless it is of an
 * exam taken. [problem] told under them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicsField(offered: List<Topic>, all: List<Topic>, draft: LessonDraft, problem: String?, onChange: (LessonDraft) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    var typedProblem by rememberSaveable { mutableStateOf<String?>(null) }
    val missing = stringResource(R.string.language_name_missing)
    val tooLong = stringResource(R.string.text_too_long, Topic.NAME_MAX)
    val closed = stringResource(R.string.lesson_topic_closed)
    val add = {
        val name = InputText.singleLine(typed)
        val same = all.find { InputText.readTheSame(it.name, name) }
        typedProblem = when {
            !InputText.isVisible(name) -> missing
            InputText.lengthOf(name) > Topic.NAME_MAX -> tooLong
            same != null && offered.none { it.id == same.id } -> closed
            else -> null
        }
        if (typedProblem == null) {
            when {
                same != null -> onChange(draft.copy(topicIds = draft.topicIds + same.id))
                draft.newTopics.none { InputText.readTheSame(it, name) } -> onChange(draft.copy(newTopics = draft.newTopics + name))
            }
            typed = ""
        }
    }
    val search = InputText.searchable(InputText.singleLine(typed))
    val shown = offered.filter { it.id in draft.topicIds || search.isEmpty() || search in InputText.searchable(it.name) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.lesson_topics), style = MaterialTheme.typography.titleSmall)
        if (shown.isNotEmpty() || draft.newTopics.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { topic ->
                    val chosen = topic.id in draft.topicIds
                    ChoiceChip(topic.name, chosen = chosen) {
                        onChange(draft.copy(topicIds = if (chosen) draft.topicIds - topic.id else draft.topicIds + topic.id))
                    }
                }
                draft.newTopics.forEach { name ->
                    ChoiceChip(name, chosen = true) { onChange(draft.copy(newTopics = draft.newTopics - name)) }
                }
            }
        }
        OutlinedTextField(
            value = typed,
            onValueChange = {
                typed = it
                typedProblem = null
            },
            label = { Text(stringResource(R.string.lesson_topic_new)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            isError = typedProblem != null,
            supportingText = { Text(typedProblem ?: stringResource(R.string.lesson_topics_hint)) },
            trailingIcon = { TextButton(onClick = add) { Text(stringResource(R.string.lesson_topic_add)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        problem?.let { Problem(it) }
    }
}

/** The price in euros, typed with a comma, how it is read while it is right; [invalid] told under it. */
@Composable
private fun PriceField(price: String, cents: Long?, invalid: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = price,
        onValueChange = { typed -> onChange(typed.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(stringResource(R.string.lesson_price)) },
        singleLine = true,
        suffix = { Text("€") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = invalid,
        supportingText = {
            Text(
                when {
                    invalid -> stringResource(R.string.lesson_price_invalid)
                    cents != null -> "${Money.format(cents)} · ${stringResource(R.string.lesson_price_hint)}"
                    else -> stringResource(R.string.lesson_price_hint)
                },
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The note in Markdown, to write or, in "Vorschau", to read as it shows, as in the notes (SPEC.md §7). */
@Composable
private fun NoteField(note: String, previewing: Boolean, onPreviewingChange: (Boolean) -> Unit, tooLong: Boolean, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to R.string.note_writing, true to R.string.note_preview).forEachIndexed { index, (preview, label) ->
                SegmentedButton(
                    selected = previewing == preview,
                    onClick = { onPreviewingChange(preview) },
                    shape = SegmentedButtonDefaults.itemShape(index, count = 2),
                    label = { Text(stringResource(label)) },
                )
            }
        }
        if (previewing) {
            val blocks = remember(note) { Markdown.parse(note) }
            if (blocks.isEmpty()) {
                Text(stringResource(R.string.lesson_note_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                MarkdownText(blocks)
            }
        } else {
            OutlinedTextField(
                value = note,
                onValueChange = onChange,
                label = { Text(stringResource(R.string.lesson_note)) },
                minLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = tooLong,
                supportingText = {
                    Column {
                        Text(stringResource(R.string.note_text_hint))
                        LengthHint(InputText.lengthOf(InputText.multiLine(note)), LessonDraft.NOTE_MAX)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The teachers not archived who do not teach [languageName] yet, one of which is to teach it too. */
@Composable
private fun OtherTeacherDialog(languageName: String, others: List<Teacher>, onChoose: (Teacher) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lesson_teacher_other_title, languageName)) },
        text = {
            Column {
                if (others.isEmpty()) {
                    Text(stringResource(R.string.lesson_teacher_other_none))
                } else {
                    Text(stringResource(R.string.lesson_teacher_other_text, languageName))
                    others.forEach { teacher ->
                        ListItem(headlineContent = { Text(teacher.name) }, modifier = Modifier.clickable { onChoose(teacher) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** What keeps [name] from naming a new language besides those [taken], in words; none if it can. */
@Composable
internal fun languageNameProblem(name: String, taken: List<String>): String? =
    when (InputText.nameProblem(name, Language.NAME_MAX, taken)) {
        NameProblem.Missing -> stringResource(R.string.language_name_missing)
        NameProblem.TooLong -> stringResource(R.string.text_too_long, Language.NAME_MAX)
        NameProblem.Taken -> stringResource(R.string.language_name_taken)
        null -> null
    }

/** What keeps [name] from naming a teacher, in words; none if it can. */
@Composable
internal fun teacherNameProblem(name: String): String? = when (TeacherDraft(name).nameProblem) {
    TeacherProblem.NameMissing -> stringResource(R.string.teacher_name_missing)
    TeacherProblem.NameTooLong -> stringResource(R.string.text_too_long, Teacher.NAME_MAX)
    else -> null
}
