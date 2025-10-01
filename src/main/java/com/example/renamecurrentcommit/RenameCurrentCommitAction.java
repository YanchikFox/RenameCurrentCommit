package com.example.renamecurrentcommit;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

        // Enable/disable action based on git repository state
        Project project = event.getProject();
        if (project != null) {
            GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
            GitRepository repo = manager.getRepositoryForFile(project.getBaseDir());

            // Disable if no repository or repository is in rebasing state
            event.getPresentation().setEnabled(repo != null && !isRepositoryRebasing(repo));
        } else {
            event.getPresentation().setEnabled(false);
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            showError(null, "No project found");
            return;
        }

        GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
        if (manager == null) {
            showError(project, "Git is not configured for this project");
            return;
        }

        List<GitRepository> repositories = manager.getRepositories();
        if (repositories.isEmpty()) {
            showError(project, "No Git repositories found in the project");
            return;
        }

        GitRepository repo = repositories.get(0);
        if (isRepositoryRebasing(repo)) {
            showError(project, "Cannot rename commit during rebase. Complete the rebase first.");
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Checking Git Repository Status") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    if (isHeadDetached(repo)) {
                        showError(project, "Cannot rename commit in detached HEAD state");
                        return;
                    }

                    boolean hasStagedChanges = hasStagedChanges(repo);
                    String commitMessage = getCurrentCommitMessage(repo);

                    if (commitMessage == null || commitMessage.isEmpty()) {
                        showError(project, "Failed to retrieve current commit message");
                        return;
                    }

                    ApplicationManager.getApplication().invokeLater(() -> {
                        showCommitDialog(event, repo, commitMessage, hasStagedChanges);
                    }, ModalityState.defaultModalityState());

                } catch (Exception e) {
                    showError(project, "Error accessing Git repository: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Shows the commit message dialog
     */
    private void showCommitDialog(AnActionEvent event, GitRepository repo, String commitMessage, boolean hasStagedChanges) {
        CommitMessageDialog dialog = new CommitMessageDialog(event.getProject(), commitMessage, hasStagedChanges);
        if (dialog.showAndGet()) {
            amendCommit(event, repo, dialog.getCommitMessage(), dialog.shouldIncludeStaged());
        }
        Disposer.dispose(dialog);
    }

    /**
     * Amends the most recent commit with new message
     */
    private void amendCommit(AnActionEvent event, GitRepository repo, String newMessage, boolean includeStaged) {
        ProgressManager.getInstance().run(new Task.Backgroundable(event.getProject(), "Renaming Commit") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText("Preparing commit amendment...");

                    // If we don't want to include staged changes, we need to stash them first
                    boolean needStash = !includeStaged && hasStagedChanges(repo);
                    if (needStash) {
                        indicator.setText("Temporarily stashing staged changes...");
                        stashStagedChanges(repo);
                    }

                    // Amend the commit
                    indicator.setText("Amending commit...");
                    GitLineHandler handler = new GitLineHandler(
                            event.getProject(),
                            repo.getRoot(),
                            GitCommand.COMMIT
                    );
                    handler.addParameters("--amend", "-m", newMessage);
                    GitCommandResult result = Git.getInstance().runCommand(handler);

                    // Restore stashed changes if needed
                    if (needStash) {
                        indicator.setText("Restoring staged changes...");
                        unstashStagedChanges(repo);
                    }

                    if (!result.success()) {
                        showError(event.getProject(), "Failed to rename commit: " + result.getErrorOutputAsJoinedString());
                        return;
                    }

                    // Update repository state
                    repo.update();

                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showInfoMessage(
                                event.getProject(),
                                "Commit message successfully updated!",
                                "Success"
                        );
                    }, ModalityState.defaultModalityState());

                } catch (Exception e) {
                    showError(event.getProject(), "Failed to rename commit: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Temporarily stash staged changes so they don't get included in the amend
     */
    private void stashStagedChanges(GitRepository repo) throws VcsException {
        GitLineHandler handler = new GitLineHandler(
                repo.getProject(),
                repo.getRoot(),
                GitCommand.STASH
        );
        handler.addParameters("push", "--keep-index", "--message", "Temporary stash for commit rename");
        Git.getInstance().runCommand(handler).getOutputOrThrow();
    }

    /**
     * Restore the stashed changes after amend
     */
    private void unstashStagedChanges(GitRepository repo) throws VcsException {
        GitLineHandler handler = new GitLineHandler(
                repo.getProject(),
                repo.getRoot(),
                GitCommand.STASH
        );
        handler.addParameters("pop");
        Git.getInstance().runCommand(handler).getOutputOrThrow();
    }

    /**
     * Checks if there are staged changes in the repository
     */
    private boolean hasStagedChanges(GitRepository repo) {
        try {
            GitLineHandler handler = new GitLineHandler(
                    repo.getProject(),
                    repo.getRoot(),
                    GitCommand.DIFF
            );
            handler.addParameters("--cached", "--name-only");
            String output = Git.getInstance().runCommand(handler).getOutputOrThrow();
            return !output.trim().isEmpty();
        } catch (Exception e) {
            return false; // Assume no staged changes on error
        }
    }

    /**
     * Checks if the HEAD is detached
     */
    private boolean isHeadDetached(GitRepository repo) {
        return repo.getState() == GitRepository.State.DETACHED;
    }

    /**
     * Checks if repository is in rebasing state
     */
    private boolean isRepositoryRebasing(GitRepository repo) {
        return repo.getState() == GitRepository.State.REBASING;
    }

    /**
     * Retrieves the current commit message from Git log
     */
    private String getCurrentCommitMessage(GitRepository repo) {
        try {
            GitLineHandler handler = new GitLineHandler(
                    repo.getProject(),
                    repo.getRoot(),
                    GitCommand.LOG
            );
            handler.addParameters("-1", "--pretty=%B");
            return Git.getInstance()
                    .runCommand(handler)
                    .getOutputOrThrow()
                    .trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Shows error message in UI thread
     */
    private void showError(Project project, String message) {
        ApplicationManager.getApplication().invokeLater(
                () -> Messages.showErrorDialog(project, message, "Error"),
                ModalityState.defaultModalityState()
        );
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}