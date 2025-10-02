package com.example.renamecurrentcommit;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Dialog for editing commit message with improved validation and UX
 */
public class CommitMessageDialog extends DialogWrapper implements Disposable {
    private final JTextArea textArea;
    private final JCheckBox includeStaged;
    private final String originalMessage;
    private final boolean hasStaged;
    private final Project project;
    private final boolean initialIncludeStagedSelection;

    public CommitMessageDialog(Project project, String originalMessage, boolean hasStaged) {
        super(project);
        this.originalMessage = originalMessage;
        this.hasStaged = hasStaged;
        this.project = project;
        this.textArea = createTextArea(originalMessage);
        this.includeStaged = new JCheckBox("Include staged changes", true);
        this.initialIncludeStagedSelection = includeStaged.isSelected();
        this.includeStaged.addActionListener(e -> validateInput());

        initDialog();
    }

    /**
     * Creates and configures the text area for commit message
     */
    private JTextArea createTextArea(String initialText) {
        JTextArea area = new JTextArea(8, 50);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setText(initialText);
        area.setFont(EditorColorsManager.getInstance()
                .getGlobalScheme()
                .getFont(EditorFontType.PLAIN));

        area.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { validateInput(); }
            public void removeUpdate(DocumentEvent e) { validateInput(); }
            public void changedUpdate(DocumentEvent e) { validateInput(); }
        });

        return area;
    }

    /**
     * Initializes dialog properties
     */
    private void initDialog() {
        setTitle("Rename Commit");
        setResizable(true);
        init();
        validateInput();
    }

    /**
     * Validates commit message with improved error handling
     */
    private void validateInput() {
        JButton okButton = getButton(getOKAction());
        if (okButton == null) return;

        String text = textArea.getText().trim();
        boolean selectionChanged = hasStaged && includeStaged.isSelected() != initialIncludeStagedSelection;

        ValidationState validationState = evaluateValidationState(text, originalMessage, selectionChanged);

        okButton.setEnabled(validationState.isValid());
        setErrorText(validationState.getErrorText());
    }

    static ValidationState evaluateValidationState(String text, String originalMessage, boolean selectionChanged) {
        String[] lines = text.split("\n", -1);
        boolean messageChanged = !text.equals(originalMessage);

        boolean isValid = !text.isEmpty() && (messageChanged || selectionChanged);
        String errorText = getValidationError(text, lines, messageChanged, selectionChanged);

        return new ValidationState(isValid, errorText);
    }

    private static String getValidationError(String text, String[] lines, boolean messageChanged, boolean selectionChanged) {
        if (text.isEmpty()) {
            return "Commit message must not be empty.";
        }
        if (!messageChanged && !selectionChanged) {
            return "Adjust the commit message or staged selection before confirming.";
        }

        // Additional validation for conventional commit format
        if (lines.length > 0 && lines[0].length() > 72) {
            return "First line should not exceed 72 characters (currently " + lines[0].length() + ")";
        }

        return null;
    }

    static final class ValidationState {
        private final boolean valid;
        private final String errorText;

        ValidationState(boolean valid, String errorText) {
            this.valid = valid;
            this.errorText = errorText;
        }

        boolean isValid() {
            return valid;
        }

        String getErrorText() {
            return errorText;
        }
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));

        // Add commit message label
        JLabel messageLabel = new JLabel("Commit message:");
        panel.add(messageLabel, BorderLayout.NORTH);

        // Add text area
        panel.add(new JBScrollPane(textArea), BorderLayout.CENTER);

        // Add staged changes warning and checkbox if there are staged changes
        if (hasStaged) {
            JPanel stagedPanel = new JPanel(new BorderLayout());
            JLabel warningLabel = new JLabel("<html><body><b>Warning:</b> You have staged changes.</body></html>");
            warningLabel.setForeground(Color.ORANGE.darker());
            stagedPanel.add(warningLabel, BorderLayout.NORTH);
            stagedPanel.add(includeStaged, BorderLayout.SOUTH);
            panel.add(stagedPanel, BorderLayout.SOUTH);
        }

        return panel;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return textArea;
    }

    public String getCommitMessage() {
        return textArea.getText().trim();
    }

    public boolean shouldIncludeStaged() {
        return !hasStaged || includeStaged.isSelected();
    }

    @Override
    public void dispose() {
        super.dispose();
    }
}