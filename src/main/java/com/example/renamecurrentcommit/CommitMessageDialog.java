package com.example.renamecurrentcommit;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Dialog for editing commit message with basic validation
 */
public class CommitMessageDialog extends DialogWrapper implements Disposable {
    private final JTextArea textArea;
    private final String originalMessage;

    public CommitMessageDialog(Project project, String originalMessage) {
        super(project);
        this.originalMessage = originalMessage;
        this.textArea = createTextArea(originalMessage);

        initDialog();
    }

    /**
     * Creates and configures the text area for commit message
     */
    private JTextArea createTextArea(String initialText) {
        JTextArea area = new JTextArea(5, 50);
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
     * Validates commit message (only checks for empty or identical messages)
     */
    private void validateInput() {
        JButton okButton = getButton(getOKAction());
        if (okButton == null) return;

        String text = textArea.getText().trim();
        boolean isValid = !text.isEmpty() && !text.equals(originalMessage);

        okButton.setEnabled(isValid);
        setErrorText(getValidationError(text));
    }

    private String getValidationError(String text) {
        if (text.isEmpty()) return "Commit message must not be empty.";
        if (text.equals(originalMessage)) return "New message must differ from original.";
        return null;
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JBScrollPane(textArea), BorderLayout.CENTER);
        return panel;
    }

    public String getCommitMessage() {
        return textArea.getText().trim();
    }

    @Override
    public void dispose() {
        super.dispose();
    }
}