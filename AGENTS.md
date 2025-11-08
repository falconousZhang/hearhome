# Repository Guidelines

## Docs
- `~/.local/README.md`: Project introduction. 
- `~/.local/TODO.md`: Personal task tracking.
- `~/.local/commits/*.md`: Commit and PR log snippets.

## Project Structure & Module Organization
The Android app sits in the `app` module. Kotlin sources live under `app/src/main/java/com/example/hearhome`: Room entities and DAOs in `data/local`, Compose screens grouped per feature folder inside `ui`, and shared helpers in `utils`. Resources reside in `app/src/main/res`; Room schemas stay in `app/schemas` for reproducible migrations. Put JVM unit tests in `app/src/test` and instrumentation or Compose UI tests in `app/src/androidTest`.

## Build, Test, and Development Commands
- `./gradlew assembleDebug` builds the debug APK for local installs.
- `./gradlew lint` runs Android lint, Compose metrics, and static checks; resolve findings before review.
- `./gradlew testDebugUnitTest` executes JVM unit tests, including DAO checks with an in-memory Room database.
- `./gradlew connectedDebugAndroidTest` runs instrumentation and Compose UI tests on a plugged-in device or emulator.

## Coding Style & Naming Conventions
Follow Kotlin style with four-space indents and explicit visibility on public APIs. Use PascalCase for classes and Compose `@Composable` functions, camelCase for methods and mutable state, and SCREAMING_SNAKE_CASE for constants. Prefer immutable data classes for UI state and keep Room queries in DAO interfaces. Align your IDE formatter with Gradle lint output before pushing.

## Testing Guidelines
Write JUnit4 tests that mirror the production package (e.g., `UserDaoTest` under `data/local`). Compose UI tests should use `createAndroidComposeRule` and live in `androidTest`. When adding Room migrations, export updated schemas with `testDebugUnitTest` and cover both success and failure paths. Protect view models and repositories with meaningful coverage before review.

## Commit & Pull Request Guidelines
Adopt the existing Conventional Commit style (`feat:`, `fix:`, `chore:`, `refactor:`) and keep subject lines under 72 characters; append short context in English or bilingual when helpful. Each pull request should include a summary of functional changes, linked issue IDs, and emulator screenshots or screen recordings for UI-facing work. Note any schema changes, new permissions, or security-sensitive updates in the description so reviewers can focus on regression risks.

## Security & Configuration Tips
Never commit secrets or API keys—store them in `local.properties` or inject via CI. Use the provided `Crypto.sha256` helper and `androidx.security:security-crypto` APIs for sensitive data. Validate incoming IDs in navigation routes and scrub logging before merging to prevent leaking personal information.
