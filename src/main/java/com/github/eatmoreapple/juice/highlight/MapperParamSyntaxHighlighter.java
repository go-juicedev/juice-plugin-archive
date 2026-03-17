package com.github.eatmoreapple.juice.highlight;

import com.github.eatmoreapple.juice.lang.MapperParamTokenType;
import com.github.eatmoreapple.juice.lexer.MapperParamLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.ui.JBColor;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;

public class MapperParamSyntaxHighlighter extends SyntaxHighlighterBase {
    private static final TextAttributesKey HASH_PARAM =
            TextAttributesKey.createTextAttributesKey("JUICE_MAPPER_HASH_PARAM",
                    new TextAttributes(new JBColor(new Color(0x0F5CC0), new Color(0x6CB6FF)),
                            null, null, null, 0));
    private static final TextAttributesKey DOLLAR_PARAM =
            TextAttributesKey.createTextAttributesKey("JUICE_MAPPER_DOLLAR_PARAM",
                    new TextAttributes(new JBColor(new Color(0xB54708), new Color(0xFFB86C)),
                            null,
                            new JBColor(new Color(0xB54708), new Color(0xFFB86C)),
                            EffectType.LINE_UNDERSCORE,
                            0));
    private static final TextAttributesKey BAD_CHARACTER =
            TextAttributesKey.createTextAttributesKey("JUICE_MAPPER_BAD_CHARACTER",
                    new TextAttributes(new JBColor(new Color(0xC62828), new Color(0xFF6B6B)),
                            null, null, null, 0));

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new MapperParamLexer();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (tokenType == MapperParamTokenType.HASH_PARAM) {
            return pack(HASH_PARAM);
        }
        if (tokenType == MapperParamTokenType.DOLLAR_PARAM) {
            return pack(DOLLAR_PARAM);
        }
        if (tokenType == TokenType.BAD_CHARACTER) {
            return pack(BAD_CHARACTER);
        }
        return EMPTY;
    }
}
