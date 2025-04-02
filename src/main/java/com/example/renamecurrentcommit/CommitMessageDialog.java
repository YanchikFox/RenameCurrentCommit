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

public class CommitMessageDialog extends DialogWrapper implements Disposable {
    private final JTextArea textArea;
    private final String originalMessage;

    public CommitMessageDialog(Project project, String originalMessage) {
        super(project);
        this.originalMessage = originalMessage;
        this.textArea = createTextArea(originalMessage);
        setTitle(RenameCommitBundle.message("dialog.title"));
        initDialog();
    }

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

    private void initDialog() {
        setResizable(true);
        init();
        validateInput();
    }

    private void validateInput() {
        JButton okButton = getButton(getOKAction());
        if (okButton == null) return;

        String text = textArea.getText().trim();
        boolean isValid = !text.isEmpty() && !text.equals(originalMessage);

        okButton.setEnabled(isValid);
        setErrorText(getValidationError(text));
    }

    private String getValidationError(String text) {
        if (text.isEmpty()) return RenameCommitBundle.message("validation.empty");
        if (text.equals(originalMessage)) return RenameCommitBundle.message("validation.identical");
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