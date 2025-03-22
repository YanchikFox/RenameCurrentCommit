package com.example.renamecurrentcommit;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class CommitMessageDialog extends DialogWrapper {
    private final JTextArea textArea;
    private final JTextArea placeholderArea;
    private final String currentCommitMessage;

    /**
     * Initializes the commit message dialog.
     */
    protected CommitMessageDialog(Project project, String placeholder) {
        super(project);
        this.currentCommitMessage = placeholder;
        this.textArea = new JTextArea(5, 50);
        this.placeholderArea = new JTextArea(placeholder);
        placeholderArea.setEditable(false);
        placeholderArea.setLineWrap(true);
        placeholderArea.setWrapStyleWord(true);
        placeholderArea.setOpaque(false);
        placeholderArea.setBorder(null);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { checkInput(); }
            @Override public void removeUpdate(DocumentEvent e) { checkInput(); }
            @Override public void changedUpdate(DocumentEvent e) { checkInput(); }
        });
        setTitle("Rename Commit");
        setResizable(true);
        init();
        checkInput();
    }

    /**
     * Checks the input field and enables/disables the OK button accordingly.
     */
    private void checkInput() {
        JButton okButton = getButton(getOKAction());
        if (okButton != null) {
            String text = textArea.getText().trim();
            okButton.setEnabled(!text.isEmpty() && !text.equals(currentCommitMessage));
        }
    }

    /**
     * Creates the main panel for the dialog.
     */
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel placeholderPanel = new JPanel(new BorderLayout());
        placeholderPanel.add(new JLabel("Current commit name:"), BorderLayout.NORTH);
        placeholderPanel.add(placeholderArea, BorderLayout.CENTER);
        panel.add(placeholderPanel, BorderLayout.NORTH);
        panel.add(new JBScrollPane(textArea), BorderLayout.CENTER);
        return panel;
    }

    /**
     * Retrieves the new commit message entered by the user.
     */
    public String getCommitMessage() {
        return textArea.getText().trim();
    }
}
