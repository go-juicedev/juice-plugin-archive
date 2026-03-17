package com.github.eatmoreapple.juice.lang;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public class MapperParamFileType extends LanguageFileType {
    public static final MapperParamFileType INSTANCE = new MapperParamFileType();

    private MapperParamFileType() {
        super(MapperParamLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "MapperParam";
    }

    @Override
    public @NotNull String getDescription() {
        return "Juice mapper parameter placeholder";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "mapperparam";
    }

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }
}
