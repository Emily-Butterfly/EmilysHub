# A pull request from Dependabot

Dependabot proposes new versions once a month (`.github/dependabot.yml`): one pull request for the Gradle libraries
and the Gradle wrapper, one for the GitHub Actions; a security fix comes at once in a pull request of its own.
**Claude checks and mends, Emily merges** (decided 14.11.2026, #115).

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

## Not proposed

- `targetSdk` (fixed at 35 by SPEC.md §12) and `minSdk` are set in `app/build.gradle.kts`, not in the catalog.
- Lint's own nags about newer versions stay off (`app/build.gradle.kts`, `lint.disable`): Dependabot tells instead.
