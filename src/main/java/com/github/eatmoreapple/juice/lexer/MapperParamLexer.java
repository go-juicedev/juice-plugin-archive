package com.github.eatmoreapple.juice.lexer;

import com.github.eatmoreapple.juice.lang.MapperParamTokenType;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MapperParamLexer extends LexerBase {
    private CharSequence buffer = "";
    private int bufferEnd;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        this.tokenStart = startOffset;
        this.tokenEnd = startOffset;
        this.tokenType = null;
        advance();
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public void advance() {
        if (tokenEnd >= bufferEnd) {
            tokenStart = bufferEnd;
            tokenType = null;
            return;
        }

        tokenStart = tokenEnd;
        char current = buffer.charAt(tokenStart);
        if ((current == '#' || current == '$') && tokenStart + 1 < bufferEnd && buffer.charAt(tokenStart + 1) == '{') {
            int closingBrace = findClosingBrace(tokenStart + 2);
            tokenEnd = closingBrace >= 0 ? closingBrace + 1 : bufferEnd;
            tokenType = current == '#' ? MapperParamTokenType.HASH_PARAM : MapperParamTokenType.DOLLAR_PARAM;
            return;
        }

        if (current == '}' || current == '{') {
            tokenEnd = tokenStart + 1;
            tokenType = TokenType.BAD_CHARACTER;
            return;
        }

        tokenEnd = tokenStart + 1;
        while (tokenEnd < bufferEnd) {
            char next = buffer.charAt(tokenEnd);
            if (next == '#' || next == '$' || next == '{' || next == '}') {
                break;
            }
            tokenEnd++;
        }
        tokenType = MapperParamTokenType.TEXT;
    }

    private int findClosingBrace(int fromIndex) {
        for (int i = fromIndex; i < bufferEnd; i++) {
            if (buffer.charAt(i) == '}') {
                return i;
            }
        }
        return -1;
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return bufferEnd;
    }
}
