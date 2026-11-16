# A pull request from Dependabot

Dependabot proposes new versions once a month (`.github/dependabot.yml`): one pull request for the Gradle libraries
and the Gradle wrapper, one for the GitHub Actions; a security fix comes at once in a pull request of its own.
**Claude checks and mends, Emily merges** (decided 14.11.2026, #115).

## What it takes on GitHub

The file alone brings the monthly version updates. A security fix at once needs two things more:

- In the repository's settings, under **Code security**: "Dependency graph", "Dependabot alerts" and "Dependabot
  security updates" switched on. Only Emily can do that.
- The job `dependencies` in the Build workflow, which tells GitHub the libraries of the Gradle build on each push to
  the default branch; GitHub cannot read them from the build by itself, and without them it warns of nothing.

Whether Dependabot takes the file shows under Insights → Dependency graph → Dependabot once it has run.

## Taking one over

1. **CI.** The Build workflow runs on Dependabot's branch as on any push, without the signing secrets: the release
   APK is then left unsigned, which is no failure. Both jobs, `build` and `instrumented`, must be green.
2. **Changelog.** Read what changed in each version proposed: breaking changes, a new minimum SDK, a changed default.
   Room and SQLCipher above all, as they hold the data: a new Room version can change the exported schema, which the
   step "Datenbank-Schema im Repo prüfen" catches; a new SQLCipher version must still open a database of the one
   before (`HubDatabaseMigrationTest` on the emulator).
3. **Mend what breaks** on Dependabot's branch, in commits of its own; never by pinning a version back without saying
   why in the pull request.
4. **Say what was checked** in one comment on the pull request: the versions, what their changelogs say that
   matters here, and what was mended. Then Emily merges.

## An alert without a pull request

Dependabot cannot propose a fix for a library no build file names: one brought in by another library (Guava through
Health Connect), or by the build's own tools (the Android Gradle plugin, lint, KSP), whose versions the plugin fixes.
Such an alert stays open without a pull request. Its fix is a line in `securityFloors` in `settings.gradle.kts`: the
lowest version the build may resolve of that library, with the advisory it answers. Every configuration of every
project, the buildscript classpath included, resolves a lower one in that version; `dependencyInsight` names the rule
as the reason. A line goes again once nothing resolves below it any more, as after an update of the plugin.

To find what the alerts are about without access to them: list what the build resolves and look each up at osv.dev,
which carries GitHub's advisories (done on 16.11.2026 for the first floors). Then the build as CI runs it, the release
build with R8 included.

## Not proposed

- `targetSdk` (fixed at 35 by SPEC.md §12) and `minSdk` are set in `app/build.gradle.kts`, not in the catalog.
- Lint's own nags about newer versions stay off (`app/build.gradle.kts`, `lint.disable`): Dependabot tells instead.
