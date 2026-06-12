package com.github.eatmoreapple.juice.lexer;

import com.github.eatmoreapple.juice.lang.MapperParamTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapperParamLexerTest {
    @Test
    void tokenizesHashAndDollarParameters() {
        MapperParamLexer lexer = new MapperParamLexer();
        lexer.start("#{id} ${name}");

        List<IElementType> tokenTypes = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            tokenTypes.add(lexer.getTokenType());
            lexer.advance();
        }

        assertEquals(List.of(
                MapperParamTokenType.HASH_PARAM,
                MapperParamTokenType.TEXT,
                MapperParamTokenType.DOLLAR_PARAM
        ), tokenTypes);
    }

    @Test
    void marksDanglingBraceAsBadCharacter() {
        MapperParamLexer lexer = new MapperParamLexer();
        lexer.start("abc}");

        List<IElementType> tokenTypes = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            tokenTypes.add(lexer.getTokenType());
            lexer.advance();
        }

        assertEquals(List.of(
                MapperParamTokenType.TEXT,
                TokenType.BAD_CHARACTER
        ), tokenTypes);
    }
}
