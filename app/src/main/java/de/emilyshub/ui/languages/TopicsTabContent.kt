package de.emilyshub.ui.languages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.data.NameProblem
import de.emilyshub.languages.Exam
import de.emilyshub.languages.ExamDraft
import de.emilyshub.languages.ExamProblem
import de.emilyshub.languages.ExamStatus
import de.emilyshub.languages.Languages
import de.emilyshub.languages.Topic
import de.emilyshub.languages.TopicGroup
import de.emilyshub.languages.TopicRow
import de.emilyshub.languages.TopicsTab
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.Problem
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.plusFabSpace
import java.io.Serializable
import java.time.LocalDate
import kotlin.math.absoluteValue

/** How far off [status]'s exam is and how its topics stand: "noch 43 Tage · 12 von 30 offen", "heute", "seit 3 Tagen". */
@Composable
internal fun examStatusText(status: ExamStatus): String {
    val days = status.days.toInt()
    val when_ = when {
        days > 0 -> pluralStringResource(R.plurals.exam_days_left, days, days)
        days == 0 -> stringResource(R.string.exam_today)
        else -> pluralStringResource(R.plurals.exam_days_since, days.absoluteValue, days.absoluteValue)
    }
    return stringResource(R.string.exam_status, when_, stringResource(R.string.exam_open_topics, status.open, status.topics))
}

/** The dialogs of the tab, one at a time, kept past a turn. */
private sealed interface TopicsDialog : Serializable {
    data object NewTopic : TopicsDialog

    data object NewExam : TopicsDialog

    data class EditExam(val examId: Long) : TopicsDialog

    data class TakeExam(val examId: Long) : TopicsDialog

    data class DeleteExam(val examId: Long) : TopicsDialog

    data class MoveOpen(val examId: Long) : TopicsDialog

    data class RenameTopic(val topicId: Long) : TopicsDialog

    data class MoveTopic(val topicId: Long) : TopicsDialog

    data class DeleteTopic(val topicId: Long) : TopicsDialog

    data class TopicLessons(val topicId: Long) : TopicsDialog
}

/**
 * The tab "Themen" of a language's page (SPEC.md §15): the next exam on top, with the days to it and its open topics;
 * then the topics grouped by exam, those to come by date, then those without one, then those taken, folded until opened.
 * A topic is done by its box, except of an exam taken; tapped, it lists the lessons that treated it, which open in their
 * editor ([onOpenLesson]). Topics and exams are made, changed, moved and deleted here, deleting asking first.
 */
@Composable
internal fun TopicsTabContent(languages: Languages, languageId: Long, tab: TopicsTab, onOpenLesson: (Long) -> Unit) {
    var dialog by rememberSaveable { mutableStateOf<TopicsDialog?>(null) }
    var unfolded by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    val scope = rememberCoroutineScope()
    val exams = tab.exams
    val groups = tab.upcoming + tab.withoutExam + tab.taken
    val topics = groups.flatMap { it.topics }.associateBy { it.topic.id }
    // An exam not taken, to move to.
    val openExams = tab.upcoming.mapNotNull { it.exam?.exam }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp).plusFabSpace()) {
        // One item, so that the next exam coming or going does not scroll the list away from it.
        item(key = "top") {
            Column {
                tab.next?.let { next ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.exam_next, next.exam.name), style = MaterialTheme.typography.titleMedium)
                            Text(examStatusText(next), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { dialog = TopicsDialog.NewTopic }) { Text(stringResource(R.string.topic_create)) }
                    OutlinedButton(onClick = { dialog = TopicsDialog.NewExam }) { Text(stringResource(R.string.exam_create)) }
                }
            }
        }
        if (groups.all { it.topics.isEmpty() } && exams.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.topics_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        val takeBack: (Long) -> Unit = { examId -> scope.launchNow { languages.setExamTaken(examId, taken = false) } }
        val onTopic = TopicActions(
            onCheck = { row, done -> scope.launchNow { languages.setTopicDone(row.topic.id, done) } },
            onOpen = { dialog = TopicsDialog.TopicLessons(it.topic.id) },
            onRename = { dialog = TopicsDialog.RenameTopic(it.topic.id) },
            onMove = { dialog = TopicsDialog.MoveTopic(it.topic.id) },
            onDelete = { dialog = TopicsDialog.DeleteTopic(it.topic.id) },
        )
        tab.upcoming.forEach { group ->
            examGroup(group, folded = false, onFold = null, exams = exams, actions = onTopic, onTakeBack = takeBack) { dialog = it }
        }
        if (tab.withoutExam.topics.isNotEmpty() || exams.isNotEmpty()) {
            item(key = "without") { GroupHeading(stringResource(R.string.topics_without_exam), subtitle = null) }
            topicRows(tab.withoutExam, exams, onTopic)
        }
        tab.taken.forEach { group ->
            val examId = checkNotNull(group.exam).exam.id
            val folded = examId !in unfolded
            examGroup(
                group, folded = folded,
                onFold = { unfolded = if (folded) unfolded + examId else unfolded - examId },
                exams = exams, actions = onTopic, onTakeBack = takeBack,
            ) { dialog = it }
        }
    }

    when (val shown = dialog) {
        null -> Unit
        TopicsDialog.NewTopic -> TopicNameDialog(
            title = R.string.topic_create, confirm = R.string.language_create_confirm, initial = "",
            taken = topics.values.map { it.topic.name },
            onConfirm = { name ->
                dialog = null
                scope.launchNow { languages.createTopic(languageId, name) }
            },
            onDismiss = { dialog = null },
        )
        TopicsDialog.NewExam -> ExamDialog(
            title = R.string.exam_create, initial = ExamDraft(), today = languages.today(),
            onConfirm = { draft ->
                dialog = null
                scope.launchNow { languages.createExam(languageId, draft) }
            },
            onDismiss = { dialog = null },
        )
        is TopicsDialog.EditExam -> exams[shown.examId]?.let { exam ->
            ExamDialog(
                title = R.string.exam_edit, initial = ExamDraft(exam.name, exam.date), today = languages.today(),
                onConfirm = { draft ->
                    dialog = null
                    scope.launchNow { languages.saveExam(exam.id, draft) }
                },
                onDismiss = { dialog = null },
            )
        }
        is TopicsDialog.TakeExam -> exams[shown.examId]?.let { exam ->
            TakeExamDialog(
                exam = exam,
                onConfirm = { result ->
                    dialog = null
                    scope.launchNow { languages.setExamTaken(exam.id, taken = true, result = result) }
                },
                onDismiss = { dialog = null },
            )
        }
        is TopicsDialog.DeleteExam -> exams[shown.examId]?.let { exam ->
            ConfirmDialog(
                title = stringResource(R.string.exam_delete_title),
                text = stringResource(R.string.exam_delete_text, exam.name),
                onConfirm = {
                    dialog = null
                    scope.launchNow { languages.deleteExam(exam.id) }
                },
                onDismiss = { dialog = null },
            )
        }
        is TopicsDialog.MoveOpen -> exams[shown.examId]?.let { exam ->
            MoveDialog(
                title = stringResource(R.string.topics_move_open_title, exam.name),
                targets = openExams.filter { it.id != exam.id },
                onChoose = { target ->
                    dialog = null
                    scope.launchNow { languages.moveOpenTopics(exam.id, target) }
                },
                onDismiss = { dialog = null },
            )
        }
        is TopicsDialog.RenameTopic -> topics[shown.topicId]?.let { row ->
            TopicNameDialog(
                title = R.string.topic_rename, confirm = R.string.language_rename_confirm, initial = row.topic.name,
                taken = topics.values.map { it.topic }.filter { it.id != row.topic.id }.map { it.name },
                onConfirm = { name ->
                    dialog = null
                    scope.launchNow { languages.renameTopic(row.topic.id, name) }
                },
                onDismiss = { dialog = null },
            )
        }
        is TopicsDialog.MoveTopic -> topics[shown.topicId]?.let { row ->
            MoveDialog(
                title = stringResource(R.string.topic_move_title, row.topic.name),
                targets = openExams.filter { it.id != row.topic.examId },
                withNone = row.topic.examId != null,
                onChoose = { target ->
                    dialog = null
                    scope.launchNow { languages.moveTopics(listOf(row.topic.id), target) }
                },
                onDismiss = { dialog = null },
            )
        }
        is TopicsDialog.DeleteTopic -> topics[shown.topicId]?.let { row ->
            ConfirmDialog(
                title = stringResource(R.string.topic_delete_title),
                text = stringResource(R.string.topic_delete_text, row.topic.name),
                onConfirm = {
                    dialog = null
                    scope.launchNow { languages.deleteTopic(row.topic.id) }
                },
                onDismiss = { dialog = null },
            )
        }
        is TopicsDialog.TopicLessons -> topics[shown.topicId]?.let { row ->
            TopicLessonsDialog(
                languages, row.topic,
                onOpenLesson = {
                    dialog = null
                    onOpenLesson(it)
                },
                onDismiss = { dialog = null },
            )
        }
    }
}

/** What a topic's line does: done or open, its lessons, renamed, moved, deleted. */
private class TopicActions(
    val onCheck: (TopicRow, Boolean) -> Unit,
    val onOpen: (TopicRow) -> Unit,
    val onRename: (TopicRow) -> Unit,
    val onMove: (TopicRow) -> Unit,
    val onDelete: (TopicRow) -> Unit,
)

/** An exam's heading, with its menu, and its topics unless [folded]; [onFold] folds or unfolds one taken. */
private fun LazyListScope.examGroup(
    group: TopicGroup,
    folded: Boolean,
    onFold: (() -> Unit)?,
    exams: Map<Long, Exam>,
    actions: TopicActions,
    onTakeBack: (Long) -> Unit,
    onDialog: (TopicsDialog) -> Unit,
) {
    val status = checkNotNull(group.exam)
    val exam = status.exam
    item(key = "exam-${exam.id}") {
        val subtitle = if (exam.taken) {
            exam.result.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.exam_taken_result, it) }
                ?: stringResource(R.string.exam_taken)
        } else {
            examStatusText(status)
        }
        GroupHeading(
            title = stringResource(R.string.exam_title_line, exam.name, exam.date.numericDate()),
            subtitle = subtitle,
            fold = onFold?.let { it to folded },
        ) {
            ExamMenu(exam, hasOpen = status.open > 0, onTakeBack = { onTakeBack(exam.id) }, onDialog = onDialog)
        }
    }
    if (!folded) topicRows(group, exams, actions)
}

/** The topics of [group], or that there are none. */
private fun LazyListScope.topicRows(group: TopicGroup, exams: Map<Long, Exam>, actions: TopicActions) {
    val key = group.exam?.exam?.id ?: "none"
    if (group.topics.isEmpty()) {
        item(key = "empty-$key") {
            Text(
                stringResource(R.string.topics_group_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    items(group.topics, key = { "topic-${it.topic.id}" }) { row -> TopicLine(row, row.checkable(exams), actions) }
}

/** A group's heading: [title], [subtitle] under it, a fold toggle if [fold] (its action and whether folded), [menu]. */
@Composable
private fun GroupHeading(
    title: String,
    subtitle: String?,
    fold: Pair<() -> Unit, Boolean>? = null,
    menu: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        fold?.let { (onFold, folded) ->
            IconButton(onClick = onFold) {
                Icon(
                    if (folded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(if (folded) R.string.exam_show else R.string.exam_hide, title),
                )
            }
        }
        menu()
    }
}

/** The menu of an exam: changed, marked taken or taken back, its open topics moved, deleted. */
@Composable
private fun ExamMenu(exam: Exam, hasOpen: Boolean, onTakeBack: () -> Unit, onDialog: (TopicsDialog) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.exam_menu, exam.name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val entries = buildList {
                add(R.string.exam_edit to TopicsDialog.EditExam(exam.id))
                if (exam.taken) {
                    add(R.string.exam_change_result to TopicsDialog.TakeExam(exam.id))
                } else {
                    add(R.string.exam_mark_taken to TopicsDialog.TakeExam(exam.id))
                }
                if (hasOpen) add(R.string.topics_move_open to TopicsDialog.MoveOpen(exam.id))
                add(R.string.delete to TopicsDialog.DeleteExam(exam.id))
            }
            entries.forEach { (label, dialog) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        onDialog(dialog)
                    },
                )
            }
            if (exam.taken) {
                // Taken back at once: nothing is lost but the result.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exam_take_back)) },
                    onClick = {
                        expanded = false
                        onTakeBack()
                    },
                )
            }
        }
    }
}

/**
 * A topic: its box to be done or open again, if [checkable]; its name, in how many lessons and when last; tapped, its
 * lessons; its menu, to rename, move or delete, of one only to read only moving it while open.
 */
@Composable
private fun TopicLine(row: TopicRow, checkable: Boolean, actions: TopicActions) {
    val topic = row.topic
    val doneLabel = stringResource(R.string.topic_done, topic.name)
    ListItem(
        leadingContent = {
            Checkbox(
                checked = topic.done,
                onCheckedChange = { actions.onCheck(row, it) },
                enabled = checkable,
                modifier = Modifier.semantics { contentDescription = doneLabel },
            )
        },
        headlineContent = { Text(topic.name) },
        supportingContent = {
            Text(
                row.last?.let { pluralStringResource(R.plurals.topic_lessons, row.lessons, row.lessons, it.numericDate()) }
                    ?: stringResource(R.string.topic_no_lessons),
            )
        },
        trailingContent = {
            if (checkable || !topic.done) TopicMenu(row, checkable, actions)
        },
        modifier = Modifier.clickable { actions.onOpen(row) },
    )
    HorizontalDivider()
}

@Composable
private fun TopicMenu(row: TopicRow, checkable: Boolean, actions: TopicActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.topic_menu, row.topic.name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val entries = buildList {
                if (checkable) add(R.string.language_rename to actions.onRename)
                add(R.string.topic_move to actions.onMove)
                if (checkable) add(R.string.delete to actions.onDelete)
            }
            entries.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        action(row)
                    },
                )
            }
        }
    }
}

/** A topic's name, new or renamed, none other of the language's [taken] reading the same. */
@Composable
private fun TopicNameDialog(title: Int, confirm: Int, initial: String, taken: List<String>, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    NameDialog(
        title = title,
        confirm = confirm,
        initial = initial,
        max = Topic.NAME_MAX,
        problemOf = { topicNameProblem(it, taken) },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** What keeps [name] from naming a topic besides those [taken], in words; none if it can. */
@Composable
internal fun topicNameProblem(name: String, taken: List<String>): String? =
    when (InputText.nameProblem(name, Topic.NAME_MAX, taken)) {
        NameProblem.Missing -> stringResource(R.string.language_name_missing)
        NameProblem.TooLong -> stringResource(R.string.text_too_long, Topic.NAME_MAX)
        NameProblem.Taken -> stringResource(R.string.topic_name_taken)
        null -> null
    }

/** An exam's name and date, new or changed; told at the field what is missing once tried. */
@Composable
private fun ExamDialog(title: Int, initial: ExamDraft, today: LocalDate, onConfirm: (ExamDraft) -> Unit, onDismiss: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    val problems = draft.problems
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val nameProblem = when {
                    ExamProblem.NameMissing in problems && (tried || draft.name.isNotEmpty()) -> stringResource(R.string.exam_name_missing)
                    ExamProblem.NameTooLong in problems -> stringResource(R.string.text_too_long, Exam.NAME_MAX)
                    else -> null
                }
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(R.string.exam_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    isError = nameProblem != null,
                    supportingText = {
                        if (nameProblem != null) Text(nameProblem) else LengthHint(InputText.lengthOf(draft.cleanedName), Exam.NAME_MAX)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { choosingDate = true }) {
                    Text(draft.date?.let { "${stringResource(R.string.exam_date)}: ${it.numericDate()}" } ?: stringResource(R.string.exam_choose_date))
                }
                if (tried && ExamProblem.DateMissing in problems) Problem(stringResource(R.string.exam_date_missing))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    tried = true
                    if (problems.isEmpty()) onConfirm(draft)
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (choosingDate) {
        DateDialog(
            initial = draft.date ?: today,
            onConfirm = {
                draft = draft.copy(date = it)
                choosingDate = false
            },
            onDismiss = { choosingDate = false },
        )
    }
}

/** [exam] marked taken, with its result as free text if wished. */
@Composable
private fun TakeExamDialog(exam: Exam, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var result by rememberSaveable { mutableStateOf(exam.result) }
    val tooLong = InputText.lengthOf(InputText.multiLine(result).trim()) > Exam.RESULT_MAX
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exam.name) },
        text = {
            OutlinedTextField(
                value = result,
                onValueChange = { result = it },
                label = { Text(stringResource(R.string.exam_result)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = tooLong,
                supportingText = { LengthHint(InputText.lengthOf(InputText.multiLine(result).trim()), Exam.RESULT_MAX) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (!tooLong) onConfirm(result) }) {
                Text(stringResource(if (exam.taken) R.string.save else R.string.exam_mark_taken))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Where to move to: one of the exams [targets], or none, if [withNone]. */
@Composable
private fun MoveDialog(title: String, targets: List<Exam>, withNone: Boolean = true, onChoose: (Long?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (targets.isEmpty() && !withNone) Text(stringResource(R.string.topics_move_none))
                targets.forEach { exam ->
                    ListItem(
                        headlineContent = { Text(exam.name) },
                        supportingContent = { Text(exam.date.numericDate()) },
                        modifier = Modifier.clickable { onChoose(exam.id) },
                    )
                }
                if (withNone) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.topics_without_exam)) },
                        modifier = Modifier.clickable { onChoose(null) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A question before deleting: [text], deleted with [onConfirm]. */
@Composable
private fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The lessons that treated [topic], the newest first, each opening in its editor. */
@Composable
private fun TopicLessonsDialog(languages: Languages, topic: Topic, onOpenLesson: (Long) -> Unit, onDismiss: () -> Unit) {
    val lessons by remember(languages, topic.id) { languages.lessonsOf(topic.id) }.collectAsState(initial = null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.topic_lessons_title, topic.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val shown = lessons
                when {
                    shown == null -> Unit
                    shown.isEmpty() -> Text(stringResource(R.string.topic_lessons_none))
                    else -> shown.forEach { row ->
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.language_lesson_line, row.lesson.date.numericDate(), durationText(row.lesson.minutes.toLong())))
                            },
                            supportingContent = { Text(row.teacher.name) },
                            modifier = Modifier.clickable { onOpenLesson(row.lesson.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
