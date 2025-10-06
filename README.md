# Rename Current Commit IntelliJ Plugin ![Gradle CI](https://github.com/YanchikFox/RenameCurrentCommit/actions/workflows/gradle-ci.yml/badge.svg)

A lightweight IntelliJ IDEA plugin that amends the most recent Git commit message directly from the IDE.

## ✨ Features
- **Rename without the terminal** – update the latest commit message from menus or a shortcut.
- **Smart Git root selection** – automatically detects the active repository or lets you choose when multiple roots are present.
- **Staged change control** – decide whether staged files should stay in the amended commit; the plugin safely stashes and restores them when excluded.
- **Commit message validation** – prevents empty submissions and warns when the summary line exceeds 72 characters.
- **Theme-aware UI** – ships with light and dark icons that match the current editor theme.

## 📥 Installation
1. Download the latest packaged plugin from the [Releases](https://github.com/YanchikFox/RenameCurrentCommit/releases) page.
2. In IntelliJ IDEA, open **File → Settings → Plugins** (or **Preferences** on macOS).
3. Click **⚙ → Install Plugin from Disk…** and choose the downloaded `.zip`.
4. Restart the IDE to activate the plugin.

> The plugin targets IntelliJ IDEA 2024.1 – 2024.3.* and requires the bundled Git4Idea plugin (enabled by default).

## 🖱 Usage
You can start the rename flow from any of the following entry points:

- **Git Main Menu** → "Rename Last Commit"
- **Git Branch Toolbar popup** (branch icon) → "Rename Last Commit"
- **Git context menu** when right-clicking in project view → "Rename Last Commit"
- **Keyboard shortcut**: `Ctrl+Shift+S` (customisable via **Settings → Keymap**)

When triggered, a dialog appears with:

1. The current commit message, ready to edit.
2. A warning if staged changes are detected, plus a checkbox to include/exclude them.
3. Inline validation with actionable error messages (e.g. empty message, summary too long).

Confirm to amend the latest commit; the plugin updates the message and restores any temporarily stashed staged changes.

## 🛠 Build & Run from Source
```bash
git clone https://github.com/YanchikFox/RenameCurrentCommit.git
cd RenameCurrentCommit
./gradlew --refresh-dependencies
./gradlew build          # Compiles and runs automated tests
./gradlew runIde         # Launches a sandbox IDE with the plugin installed
```

Gradle downloads all required IntelliJ platform dependencies on the first run. Use `./gradlew test` to execute the unit test suite separately.

## 📝 License
Licensed under the [MIT License](./LICENSE).
