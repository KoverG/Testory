# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run

```bash
# Run the application
mvn clean javafx:run

# Build
mvn clean install
```

No automated test suite exists yet (no `src/test/java`).

## Architecture

**Type:** JavaFX desktop app for QA test case and test cycle management.
**Language:** Java 23+, JavaFX 23.0.2, FXML + CSS, JSON file persistence.
**Entry point:** `app.MainApp` → loads `shell.fxml` → `ShellController`.

### Layer structure

```
app/
├── core/       # AppConfig, AppSettings, Router, I18n, Fxml loader
├── shell/      # ShellController (main layout), DrawerEngine, HomeController
├── ui/         # Reusable UI components (ToggleSwitch, UiSvg, UiBlur, etc.)
└── domain/
    ├── testcases/   # TestCase model, FileTestCaseRepository, use cases, UI controllers
    ├── cycles/      # Cycle model, FileCycleRepository, CycleRunState, UI controllers
    └── history/     # HistoryDayDataService, HistoryMonthDataService, HistoryScreen
```

### Navigation

`Router` is a central singleton with a back-stack. It drives navigation via the `View` enum (HOME, TEST_CASES, CYCLES, HISTORY, ANALYTICS, REPORTS). The shell controller subscribes via `Router.setOnHeaderTitle()`.

### Persistence

All data lives in the working directory (detected at startup by locating `app-setting.json`):
- `config/app.properties` — version (`app.version`)
- `config/app-setting.json` — user preferences (theme, language)
- `config/testcases.json` / `config/case-history-index.json` — indexes
- `test_resources/test_cases/*.json` — individual test case files
- `test_resources/cycles/*.json` — individual cycle run files

ID format: `tc_YYYYMMDDHHMMSSmmm_RRR` (test cases), `cy_...` (cycles).

### Localization

`I18n` loads `ResourceBundle` (Russian/English). Properties files with Cyrillic are encoding-sensitive — see rules below.

### Theme

Light/dark theme is toggled via `root.styleClass` on the existing `Scene` — do **not** recreate the `Scene`. Inline-styled elements must be updated separately on theme change.

## Critical Rules (from AGENTS.md)

### Versioning and branches
- Classify every task as PATCH / MINOR / MAJOR relative to `config/app.properties`.
- Propose the target version and branch name (`codex/<version>-<short-name>`) **before** creating the branch.
- Create the branch from `master` only after user confirmation.

### Before making changes
- Study the current implementation of the relevant area first.
- Briefly describe what was found before starting edits.
- If multiple UX or architectural variants exist, propose the best one before implementing.

### Cyrillic / encoding safety
- Never break Russian text or convert it to `\u....` escape sequences.
- Never use BOM in files.
- Do not blindly rewrite entire files that contain Cyrillic text — make targeted edits only.
- If `Edit` (patch) fails on a Cyrillic file, do not fall back to full rewrite; find an ASCII-only change path or ask the user for a safe approach.

### Commits
- Before committing: re-evaluate PATCH/MINOR/MAJOR; update `app.version` in `config/app.properties` to match the branch version.
- Show the Russian commit message to the user **before** committing.
- Write commit messages in Russian, perfective aspect, impersonal form (действие уже выполнено).
- Use `git commit -m "..."` for Russian messages (not PowerShell pipe/stdin). Fall back to `git commit -F <utf8-no-bom-file>` only if `-m` is not applicable.
- Commit and push only after explicit user approval.

### Git safety
- Never run multiple git state-changing commands (`checkout`, `commit`, `merge`, etc.) in parallel.
- After any branch-switching command, verify the current branch with `git branch --show-current` before the next step.
- Before `commit`, verify with `git status --short --branch` that the correct branch is active.

### Critical incidents
- If a critical mistake occurs: acknowledge it explicitly, describe the root cause, fix the state, then add a preventive rule to `AGENTS.md`.

## My preferences

Always answer in Russian.

Explain briefly and clearly, without unnecessary theory.

Before making changes:
- analyze existing code
- describe what you found
- propose the best solution

When writing code:
- write production-ready code
- avoid unnecessary abstractions
- keep it simple and readable