package com.example.renamecurrentcommit;

import com.intellij.AbstractBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class RenameCommitBundle extends AbstractBundle {
    @NonNls private static final String BUNDLE = "messages.RenameCommitBundle";
    private static final RenameCommitBundle INSTANCE = new RenameCommitBundle();

    private RenameCommitBundle() {
        super(BUNDLE);
    }

    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                 @NotNull Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}