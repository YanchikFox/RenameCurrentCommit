package com.example.renamecurrentcommit;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.repo.GitRepositoryImpl;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RenameCurrentCommitActionTest extends BasePlatformTestCase {

    private VirtualFile repoOneRoot;
    private VirtualFile repoTwoRoot;
    private Path repoOnePath;
    private Path repoTwoPath;
    private GitRepository repoOne;
    private GitRepository repoTwo;
    private List<VcsDirectoryMapping> previousMappings;

    @Override
    protected void setUp() throws Exception {
        super.setUp();


        String basePath = getProject().getBasePath();
        assertNotNull("Project base path should be available", basePath);
        Path projectBase = Path.of(basePath);

        repoOnePath = Files.createDirectories(projectBase.resolve("repo-one"));
        repoTwoPath = Files.createDirectories(projectBase.resolve("repo-two"));

        repoOneRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(repoOnePath.toString());
        repoTwoRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(repoTwoPath.toString());

        assertNotNull("Failed to create first repository root", repoOneRoot);
        assertNotNull("Failed to create second repository root", repoTwoRoot);

        initRepository(repoOnePath, "Initial commit in repo one", "one.txt");
        initRepository(repoTwoPath, "Initial commit in repo two", "two.txt");

        VfsUtil.markDirtyAndRefresh(false, true, true, repoOneRoot, repoTwoRoot);

        registerMappings();

        GitRepositoryManager manager = GitUtil.getRepositoryManager(getProject());
        manager.updateAllRepositories();

        repoOne = ensureRepositoryRegistered(manager, repoOneRoot);
        repoTwo = ensureRepositoryRegistered(manager, repoTwoRoot);

        assertNotNull("First repository should be registered", repoOne);
        assertNotNull("Second repository should be registered", repoTwo);
    }

    public void testActionEnabledAndAmendsSelectedRepository() throws Exception {
        VirtualFile contextFile = repoTwoRoot.findChild("two.txt");
        assertNotNull(contextFile);
        assertNotNull("Context file should resolve to repository",
                GitUtil.getRepositoryManager(getProject()).getRepositoryForFileQuick(contextFile));

        TestRenameCurrentCommitAction action = new TestRenameCurrentCommitAction("Renamed commit message");

        AnActionEvent updateEvent = createEvent(action, contextFile);
        action.update(updateEvent);
        assertTrue("Action should be enabled when repository can be resolved", updateEvent.getPresentation().isEnabled());

        AnActionEvent performEvent = createEvent(action, contextFile);
        action.actionPerformed(performEvent);

        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

        assertEquals("Repository two should receive amended message",
                "Renamed commit message",
                readHeadMessage(repoTwoPath));
        assertEquals("Repository one should remain untouched",
                "Initial commit in repo one",
                readHeadMessage(repoOnePath));
    }


    private void registerMappings() {
        ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(getProject());
        previousMappings = new ArrayList<>(vcsManager.getDirectoryMappings());
        List<VcsDirectoryMapping> mappings = new ArrayList<>(previousMappings);
        mappings.add(new VcsDirectoryMapping(repoOneRoot.getPath(), GitVcs.NAME));
        mappings.add(new VcsDirectoryMapping(repoTwoRoot.getPath(), GitVcs.NAME));
        vcsManager.setDirectoryMappings(mappings);
        vcsManager.updateActiveVcss();
    }

    private void initRepository(Path root, String message, String fileName) throws Exception {
        runGit(root, "init");
        runGit(root, "config", "user.name", "Test User");
        runGit(root, "config", "user.email", "test@example.com");

        Path file = root.resolve(fileName);
        Files.writeString(file, "content", StandardCharsets.UTF_8);
        runGit(root, "add", fileName);
        runGit(root, "commit", "-m", message);
    }

    private String readHeadMessage(Path root) throws Exception {
        return runGit(root, "log", "-1", "--pretty=%B").trim();
    }

    private String runGit(Path workingDir, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        Map<String, String> environment = builder.environment();
        environment.put("GIT_AUTHOR_NAME", "Test User");
        environment.put("GIT_AUTHOR_EMAIL", "test@example.com");
        environment.put("GIT_COMMITTER_NAME", "Test User");
        environment.put("GIT_COMMITTER_EMAIL", "test@example.com");

        Process process = builder.start();
        byte[] stdout = process.getInputStream().readAllBytes();
        byte[] stderr = process.getErrorStream().readAllBytes();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("git command failed: " + String.join(" ", command)
                    + "\nstdout: " + new String(stdout, StandardCharsets.UTF_8)
                    + "\nstderr: " + new String(stderr, StandardCharsets.UTF_8));
        }

        return new String(stdout, StandardCharsets.UTF_8);
    }

    private AnActionEvent createEvent(RenameCurrentCommitAction action, VirtualFile file) {
        var dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, getProject())
                .add(CommonDataKeys.VIRTUAL_FILE, file)
                .build();
        return AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext);
    }

    private GitRepository ensureRepositoryRegistered(GitRepositoryManager manager, VirtualFile root) {
        GitRepository repository = manager.getRepositoryForRootQuick(root);
        if (repository != null) {
            return repository;
        }

        repository = GitRepositoryImpl.createInstance(root, getProject(), getTestRootDisposable(), true);
        try {
            var method = com.intellij.dvcs.repo.AbstractRepositoryManager.class
                    .getDeclaredMethod("addExternalRepository", VirtualFile.class, com.intellij.dvcs.repo.Repository.class);
            method.setAccessible(true);
            method.invoke(manager, root, repository);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register repository", e);
        }
        return repository;
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (previousMappings != null) {
                ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(getProject());
                vcsManager.setDirectoryMappings(previousMappings);
                vcsManager.updateActiveVcss();
            }
        } finally {
            super.tearDown();
        }
    }

    private static class TestRenameCurrentCommitAction extends RenameCurrentCommitAction {
        private final String message;

        private TestRenameCurrentCommitAction(String message) {
            this.message = message;
        }

        @Override
        protected CommitMessageDialog createCommitDialog(@NotNull com.intellij.openapi.project.Project project,
                                                          String commitMessage,
                                                          boolean hasStagedChanges) {
            return new CommitMessageDialog(project, commitMessage, hasStagedChanges) {
                @Override
                public boolean showAndGet() {
                    return true;
                }

                @Override
                public String getCommitMessage() {
                    return message;
                }

                @Override
                public boolean shouldIncludeStaged() {
                    return false;
                }
            };
        }
    }
}
