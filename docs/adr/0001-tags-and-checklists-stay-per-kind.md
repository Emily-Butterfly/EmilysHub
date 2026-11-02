# Tags and checklists stay per kind of entry

Routines, to-dos, goals and notes each save their tags through their own DAO (`untagAll`, then `tag`), and routines and
to-dos each save and tick their own checklist. The second architecture review (docs/architecture/deepening-plan-2.md,
§5) proposed a shared `Tagging` and a shared `Checklist` module, and we decided against both: they fail the deletion
test. Room cannot name a table at run time, so a `Tagging` module would keep the four join tables' queries one by one
and only pass them through, around a two-line save; and the two checklists differ where it matters (a routine's ticks
belong to each occurrence, a to-do's to the to-do), so a shared module would be a seam with two adapters for some 30
lines. The duplication is shallow, and each copy sits next to the entry it belongs to.

Revisit if a fifth kind of entry gets tags or a checklist, or if tagging gains a rule beyond "replace the set, leaving
out tags deleted meanwhile".
