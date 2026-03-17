package com.github.eatmoreapple.juice.lang;

import com.github.eatmoreapple.juice.lexer.MapperParamLexer;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class MapperParamParserDefinition implements ParserDefinition {
    private static final IFileElementType FILE = new IFileElementType(MapperParamLanguage.INSTANCE);
    private static final PsiParser PARSER = new PsiParser() {
        @Override
        public @NotNull ASTNode parse(@NotNull com.intellij.psi.tree.IElementType root, @NotNull PsiBuilder builder) {
            PsiBuilder.Marker marker = builder.mark();
            while (!builder.eof()) {
                builder.advanceLexer();
            }
            marker.done(root);
            return builder.getTreeBuilt();
        }
    };

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new MapperParamLexer();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return PARSER;
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return TokenSet.EMPTY;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return TokenSet.EMPTY;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return TokenSet.EMPTY;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        return new ASTWrapperPsiElement(node);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new PsiFileBase(viewProvider, MapperParamLanguage.INSTANCE) {
            @Override
            public @NotNull com.intellij.openapi.fileTypes.FileType getFileType() {
                return MapperParamFileType.INSTANCE;
            }
        };
    }

    @Override
    public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }
}
