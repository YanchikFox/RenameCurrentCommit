package com.example.renamecurrentcommit;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class RenameCurrentCommitAction extends AnAction {

    /**
     * Sets the icon for the action in the toolbar or menu.
     */
    @Override
    public void update(AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        presentation.setIcon(IconLoader.getIcon("/icons/rename_icon.svg", getClass()));
    }

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

        // Retrieve the Git repository and proceed with renaming the commit if found
        GitUtil.getRepositoryManager(project).getRepositories().stream().findFirst()
                .ifPresentOrElse(repo -> getCurrentCommitMessage(repo, oldCommitMessage ->
                                SwingUtilities.invokeLater(() -> {
                                    // Display a dialog to get a new commit message from the user
                                    CommitMessageDialog dialog = new CommitMessageDialog(project, oldCommitMessage);
                                    if (dialog.showAndGet()) {
                                        renameCommit(event, repo, dialog.getCommitMessage());
                                    }
                                })),
                        () -> Messages.showErrorDialog("Git repository not found", "Error"));
    }

    /**
     * Renames (amends) the most recent commit with the new message provided by the user.
     */
    private void renameCommit(AnActionEvent event, GitRepository repo, String newMessage) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                // Create a Git command to amend the last commit with a new message
                GitLineHandler handler = new GitLineHandler(event.getProject(), repo.getRoot(), GitCommand.COMMIT);
                handler.addParameters("--amend", "-m", newMessage);
                Git.getInstance().runCommand(handler);

                // Update the repository to reflect the changes
                repo.update();
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                        Messages.showErrorDialog("Failed to rename commit: " + e.getMessage(), "Error"));
            }
        });
    }

    /**
     * Retrieves the current commit message from the Git repository.
     */
    private void getCurrentCommitMessage(GitRepository repo, java.util.function.Consumer<String> callback) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                // Create a Git command to fetch the latest commit message
                GitLineHandler handler = new GitLineHandler(repo.getProject(), repo.getRoot(), GitCommand.LOG);
                handler.addParameters("-1", "--pretty=%B");

                // Execute the command and pass the output to the callback function
                callback.accept(Git.getInstance().runCommand(handler).getOutputOrThrow().trim());
            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                        Messages.showErrorDialog("Failed to get commit message: " + e.getMessage(), "Error"));
            }
        });
    }

    /**
     * Specifies that this action should be executed in the background thread (BGT).
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
