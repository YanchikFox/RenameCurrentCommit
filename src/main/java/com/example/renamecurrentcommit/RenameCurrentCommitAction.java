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

public class RenameCurrentCommitAction extends AnAction {
    @Override
    public void update(@NotNull AnActionEvent event) {
        boolean isDarkTheme = EditorColorsManager.getInstance().isDarkEditor();
        String iconPath = isDarkTheme ? "/icons/rename_icon_light.svg" : "/icons/rename_icon_dark.svg";
        event.getPresentation().setIcon(IconLoader.getIcon(iconPath, getClass()));
        event.getPresentation().setText(RenameCommitBundle.message("action.text"));
        event.getPresentation().setDescription(RenameCommitBundle.message("action.description"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            showError(RenameCommitBundle.message("error.no_project"));
            return;
        }

        GitUtil.getRepositoryManager(project).getRepositories().stream().findFirst()
                .ifPresentOrElse(
                        repo -> fetchAndShowCommitDialog(event, repo),
                        () -> showError(RenameCommitBundle.message("error.no_repository"))
                );
    }

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
                showError(RenameCommitBundle.message("error.rename_failed", e.getMessage()));
            }
        });
    }

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
                showError(RenameCommitBundle.message("error.get_message_failed", e.getMessage()));
            }
        });
    }

    private void showError(String message) {
        ApplicationManager.getApplication().invokeLater(
                () -> Messages.showErrorDialog(message, RenameCommitBundle.message("error.dialog_title")),
                ModalityState.defaultModalityState()
        );
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}