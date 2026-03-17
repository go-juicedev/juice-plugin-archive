package com.github.eatmoreapple.juice.highlight;

import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

public class MapperParamSyntaxHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
    @Override
    protected @NotNull SyntaxHighlighter createHighlighter() {
        return new MapperParamSyntaxHighlighter();
    }
}
