package com.example.renamecurrentcommit;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;

import javax.swing.*;

public class RenameCurrentCommitAction extends AnAction {
    /**
     * Handles the action of renaming the current commit message.
     */
    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            Messages.showErrorDialog("No project found", "Error");
            return;
        }

        GitUtil.getRepositoryManager(project).getRepositories().stream().findFirst()
                .ifPresentOrElse(repo -> getCurrentCommitMessage(repo, oldCommitMessage -> SwingUtilities.invokeLater(() -> {
                            CommitMessageDialog dialog = new CommitMessageDialog(project, oldCommitMessage);
                            if (dialog.showAndGet()) {
                                renameCommit(event, repo, dialog.getCommitMessage());
                            }
                        })),
                        () -> Messages.showErrorDialog("Git repository not found", "Error"));
    }

    /**
     * Renames the most recent commit with the new message provided by the user.
     */
    private void renameCommit(AnActionEvent event, GitRepository repo, String newMessage) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                GitLineHandler handler = new GitLineHandler(event.getProject(), repo.getRoot(), GitCommand.COMMIT);
                handler.addParameters("--amend", "-m", newMessage);
                Git.getInstance().runCommand(handler);
                repo.update();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> Messages.showErrorDialog("Failed to rename commit: " + e.getMessage(), "Error"));
            }
        });
    }

    /**
     * Retrieves the current commit message from the Git repository.
     */
    private void getCurrentCommitMessage(GitRepository repo, java.util.function.Consumer<String> callback) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                GitLineHandler handler = new GitLineHandler(repo.getProject(), repo.getRoot(), GitCommand.LOG);
                handler.addParameters("-1", "--pretty=%B");
                callback.accept(Git.getInstance().runCommand(handler).getOutputOrThrow().trim());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> Messages.showErrorDialog("Failed to get commit message: " + e.getMessage(), "Error"));
            }
        });
    }
}