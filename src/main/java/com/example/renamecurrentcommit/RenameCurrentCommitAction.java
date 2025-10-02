package com.example.renamecurrentcommit;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * IntelliJ IDEA action that allows renaming the most recent Git commit.
 */
public class RenameCurrentCommitAction extends AnAction {

    private static final Key<GitRepository> LAST_USED_REPOSITORY = Key.create("rename.current.commit.last.repository");

    @Override
    public void update(@NotNull AnActionEvent event) {
        // Dynamic icon based on IDE theme
        boolean isDarkTheme = EditorColorsManager.getInstance().isDarkEditor();
        String iconPath = isDarkTheme ? "/icons/rename_icon_light.svg" : "/icons/rename_icon_dark.svg";
        event.getPresentation().setIcon(IconLoader.getIcon(iconPath, getClass()));

        // Enable/disable action based on git repository state
        Project project = event.getProject();
        if (project == null) {
            event.getPresentation().setEnabled(false);
            return;
        }

        GitRepository repo = findRepository(project, event, false);
        boolean enabled = repo != null
                && repo.getCurrentRevision() != null
                && !isRepositoryInConflictingState(repo);

        event.getPresentation().setEnabled(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            showError(null, "No project found");
            return;
        }

        GitRepositoryManager manager = GitUtil.getRepositoryManager(project);

        List<GitRepository> repositories = manager.getRepositories();
        if (repositories.isEmpty()) {
            showError(project, "No Git repositories found in the project");
            return;
        }

        GitRepository repo = findRepository(project, event, true);
        if (repo == null) {
            showError(project, "Cannot determine Git repository for this operation");
            return;
        }

        if (repo.getCurrentRevision() == null) {
            showError(project, "Repository does not contain commits to rename yet");
            return;
        }

        if (isRepositoryInConflictingState(repo)) {
            showError(project, "Cannot rename commit while Git is performing another operation (rebase, merge, etc.)");
            return;
        }

        runBackgroundTask(project, "Checking git repository status", indicator -> {
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
        });
    }

    /**
     * Shows the commit message dialog
     */
    private void showCommitDialog(AnActionEvent event, GitRepository repo, String commitMessage, boolean hasStagedChanges) {
        CommitMessageDialog dialog = createCommitDialog(event.getProject(), commitMessage, hasStagedChanges);
        if (dialog.showAndGet()) {
            amendCommit(event, repo, dialog.getCommitMessage(), dialog.shouldIncludeStaged());
        }
        Disposer.dispose(dialog);
    }

    protected CommitMessageDialog createCommitDialog(Project project, String commitMessage, boolean hasStagedChanges) {
        return new CommitMessageDialog(project, commitMessage, hasStagedChanges);
    }

    /**
     * Amends the most recent commit with new message
     */
    private void amendCommit(AnActionEvent event, GitRepository repo, String newMessage, boolean includeStaged) {
        runBackgroundTask(event.getProject(), "Renaming commit", indicator -> {
            try {
                indicator.setText("Preparing commit amendment...");

                boolean needStash = !includeStaged && hasStagedChanges(repo);
                boolean stashCreated = false;

                try {
                    if (needStash) {
                        indicator.setText("Temporarily stashing staged changes...");
                        stashCreated = stashStagedChanges(repo);
                    }

                    indicator.setText("Amending commit...");
                    GitLineHandler handler = new GitLineHandler(
                            event.getProject(),
                            repo.getRoot(),
                            GitCommand.COMMIT
                    );
                    handler.addParameters("--amend", "-m", newMessage);
                    GitCommandResult result = Git.getInstance().runCommand(handler);

                    if (!result.success()) {
                        throw new VcsException(result.getErrorOutputAsJoinedString());
                    }

                    repo.update();

                    if (!ApplicationManager.getApplication().isUnitTestMode()) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                Messages.showInfoMessage(
                                        event.getProject(),
                                        "Commit message successfully updated!",
                                        "Success"
                                ), ModalityState.defaultModalityState());
                    }
                } finally {
                    if (stashCreated) {
                        indicator.setText("Restoring staged changes...");
                        try {
                            unstashStagedChanges(repo);
                        } catch (VcsException e) {
                            showError(event.getProject(), "Commit renamed, but failed to restore staged changes: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                showError(event.getProject(), "Failed to rename commit: " + e.getMessage());
            }
        });
    }

    /**
     * Temporarily stash staged changes so they don't get included in the amend
     */
    private boolean stashStagedChanges(GitRepository repo) throws VcsException {
        GitLineHandler handler = new GitLineHandler(
                repo.getProject(),
                repo.getRoot(),
                GitCommand.STASH
        );
        handler.addParameters("push", "--staged", "--message", "Temporary stash for commit rename");
        GitCommandResult result = Git.getInstance().runCommand(handler);

        if (result.success()) {
            return true;
        }

        // Fallback for Git versions without --staged support
        GitLineHandler fallback = new GitLineHandler(
                repo.getProject(),
                repo.getRoot(),
                GitCommand.STASH
        );
        fallback.addParameters("push", "--message", "Temporary stash for commit rename");
        GitCommandResult fallbackResult = Git.getInstance().runCommand(fallback);
        if (!fallbackResult.success()) {
            throw new VcsException(fallbackResult.getErrorOutputAsJoinedString());
        }
        return true;
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
        handler.addParameters("pop", "--index");
        GitCommandResult result = Git.getInstance().runCommand(handler);
        if (!result.success()) {
            throw new VcsException(result.getErrorOutputAsJoinedString());
        }
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
    private boolean isRepositoryInConflictingState(GitRepository repo) {
        return repo.getState() != GitRepository.State.NORMAL;
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

    private GitRepository findRepository(Project project, AnActionEvent event, boolean allowSelection) {
        GitRepositoryManager manager = GitUtil.getRepositoryManager(project);

        VirtualFile contextFile = event != null ? event.getData(CommonDataKeys.VIRTUAL_FILE) : null;
        if (contextFile != null) {
            GitRepository repo = manager.getRepositoryForFileQuick(contextFile);
            if (repo != null) {
                project.putUserData(LAST_USED_REPOSITORY, repo);
                return repo;
            }
        }

        GitRepository repo = GitBranchUtil.getCurrentRepository(project);
        if (repo != null) {
            project.putUserData(LAST_USED_REPOSITORY, repo);
            return repo;
        }

        VirtualFile baseDir = project.getBaseDir();
        if (baseDir != null) {
            repo = manager.getRepositoryForFileQuick(baseDir);
            if (repo != null) {
                project.putUserData(LAST_USED_REPOSITORY, repo);
                return repo;
            }
        }

        GitRepository lastUsed = project.getUserData(LAST_USED_REPOSITORY);
        if (lastUsed != null && !lastUsed.isDisposed()) {
            return lastUsed;
        }

        List<GitRepository> repositories = manager.getRepositories();
        if (repositories.isEmpty()) {
            return null;
        }

        if (repositories.size() == 1) {
            GitRepository single = repositories.get(0);
            project.putUserData(LAST_USED_REPOSITORY, single);
            return single;
        }

        GitRepository chosen = selectRepository(project, repositories, allowSelection);
        if (chosen != null) {
            project.putUserData(LAST_USED_REPOSITORY, chosen);
        }
        return chosen;
    }

    private GitRepository selectRepository(Project project, List<GitRepository> repositories, boolean allowSelection) {
        GitRepository current = GitBranchUtil.getCurrentRepository(project);
        if (current != null) {
            return current;
        }

        GitRepository stored = project.getUserData(LAST_USED_REPOSITORY);
        if (stored != null && !stored.isDisposed() && repositories.contains(stored)) {
            return stored;
        }

        if (!allowSelection) {
            return repositories.stream()
                    .filter(Objects::nonNull)
                    .min(Comparator.comparing(repo -> repo.getRoot().getPresentableUrl()))
                    .orElse(null);
        }

        if (ApplicationManager.getApplication().isUnitTestMode()) {
            return repositories.stream()
                    .filter(Objects::nonNull)
                    .min(Comparator.comparing(repo -> repo.getRoot().getPresentableUrl()))
                    .orElse(null);
        }

        GitRepository[] selected = new GitRepository[1];
        Runnable chooser = () -> {
            String[] options = repositories.stream()
                    .map(repo -> repo.getRoot().getPresentableUrl())
                    .toArray(String[]::new);
            String defaultChoice = options.length > 0 ? options[0] : null;
            int selectionIndex = Messages.showChooseDialog(
                    project,
                    "Select the Git root to rename the current commit",
                    "Select Git Repository",
                    null,
                    options,
                    defaultChoice
            );
            if (selectionIndex >= 0 && selectionIndex < repositories.size()) {
                selected[0] = repositories.get(selectionIndex);
            }
        };

        if (ApplicationManager.getApplication().isDispatchThread()) {
            chooser.run();
        } else {
            ApplicationManager.getApplication().invokeAndWait(chooser, ModalityState.defaultModalityState());
        }

        if (selected[0] == null) {
            return repositories.get(0);
        }

        return selected[0];
    }

    private void runBackgroundTask(Project project, String title, Consumer<ProgressIndicator> task) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            try {
                ApplicationManager.getApplication()
                        .executeOnPooledThread(() -> task.accept(new ProgressIndicatorBase()))
                        .get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to run background task", e);
            }
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                task.accept(indicator);
            }
        });
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