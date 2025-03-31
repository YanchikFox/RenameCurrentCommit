package com.example.renamecurrentcommit;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

/**
 * IntelliJ IDEA action that allows renaming the most recent Git commit.
 */
public class RenameCurrentCommitAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent event) {
        // Dynamic icon based on IDE theme
        boolean isDarkTheme = EditorColorsManager.getInstance().isDarkEditor();
        String iconPath = isDarkTheme ? "/icons/rename_icon_light.svg" : "/icons/rename_icon_dark.svg";
        event.getPresentation().setIcon(IconLoader.getIcon(iconPath, getClass()));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            showError("No project found");
            return;
        }

        GitUtil.getRepositoryManager(project).getRepositories().stream().findFirst()
                .ifPresentOrElse(
                        repo -> fetchAndShowCommitDialog(event, repo),
                        () -> showError("Git repository not found")
                );
    }

    /**
     * Fetches current commit message and shows rename dialog
     */
    private void fetchAndShowCommitDialog(AnActionEvent event, GitRepository repo) {
        getCurrentCommitMessage(repo, commitMessage ->
                ApplicationManager.getApplication().invokeLater(() -> {
                    CommitMessageDialog dialog = new CommitMessageDialog(event.getProject(), commitMessage);
                    if (dialog.showAndGet()) {
                        amendCommit(event, repo, dialog.getCommitMessage());
                    }
                    Disposer.dispose(dialog);
                }, ModalityState.defaultModalityState())
        );
    }

    /**
     * Amends the most recent commit with new message
     */
    private void amendCommit(AnActionEvent event, GitRepository repo, String newMessage) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                GitLineHandler handler = new GitLineHandler(
                        event.getProject(),
                        repo.getRoot(),
                        GitCommand.COMMIT
                );
                handler.addParameters("--amend", "-m", newMessage);
                Git.getInstance().runCommand(handler);
                repo.update();
            } catch (Exception e) {
                showError("Failed to rename commit: " + e.getMessage());
            }
        });
    }

    /**
     * Retrieves the current commit message from Git log
     */
    private void getCurrentCommitMessage(GitRepository repo, java.util.function.Consumer<String> callback) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                GitLineHandler handler = new GitLineHandler(
                        repo.getProject(),
                        repo.getRoot(),
                        GitCommand.LOG
                );
                handler.addParameters("-1", "--pretty=%B");
                String message = Git.getInstance()
                        .runCommand(handler)
                        .getOutputOrThrow()
                        .trim();
                callback.accept(message);
            } catch (Exception e) {
                showError("Failed to get commit message: " + e.getMessage());
            }
        });
    }

    /**
     * Shows error message in UI thread
     */
    private void showError(String message) {
        ApplicationManager.getApplication().invokeLater(
                () -> Messages.showErrorDialog(message, "Error"),
                ModalityState.defaultModalityState()
        );
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}