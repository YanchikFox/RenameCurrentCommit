package com.example.renamecurrentcommit;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.awt.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class CommitMessageDialog extends DialogWrapper {
    private final JTextArea textArea;
    private final String currentCommitMessage;

    /**
     * Initializes the commit message dialog.
     */
    protected CommitMessageDialog(Project project, String placeholder) {
        super(project);
        this.currentCommitMessage = placeholder;
        this.textArea = new JTextArea(5, 50);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setText(placeholder);
        textArea.setFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                checkInput();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                checkInput();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                checkInput();
            }
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