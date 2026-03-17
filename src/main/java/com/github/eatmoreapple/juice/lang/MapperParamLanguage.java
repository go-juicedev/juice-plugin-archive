package com.github.eatmoreapple.juice.lang;

import com.intellij.lang.Language;

public class MapperParamLanguage extends Language {
    public static final MapperParamLanguage INSTANCE = new MapperParamLanguage();

    private MapperParamLanguage() {
        super("MapperParam");
    }
}
