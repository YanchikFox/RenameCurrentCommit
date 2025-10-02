# Rename Current Git Commit Plugin

A lightweight plugin that lets you quickly amend the most recent Git commit message directly from IntelliJ's UI.

## ✨ Features
- **One-click rename** - Edit commit messages without terminal commands
- **Seamless integration** - Available in Git toolbar and context menus
- **Cross-platform** - Works on Windows, macOS, and Linux

## 📦 Installation
1. Download the latest release from the [Releases](https://github.com/YanchikFox/RenameCurrentCommit/releases) page.
2. In IntelliJ IDEA, go to **File** → **Settings** → **Plugins**.
3. Click **Install Plugin from Disk...** and select the downloaded `.zip` file.
4. Restart IntelliJ IDEA to apply changes.

## 🖱 Usage
There are four ways to access:

1. **Main menu**: Click Git icon in main menu → "Rename Last Commit"
2. **Shortcut**: Default `Ctrl+Shift+S` (customizable)
3. **Context menu**: Right-click → Git → "Rename Last Commit"
4. **Git Branch Toolbar**: Click branch icon in toolbar → "Rename Last Commit"

## 🛠 Build from Source
To build and run the plugin locally:

```sh
git clone https://github.com/YanchikFox/RenameCurrentCommit
cd RenameCurrentCommit
./gradlew --refresh-dependencies
./gradlew build
./gradlew runIde
```

The Gradle wrapper will download all required dependencies automatically on the first run of these commands.
