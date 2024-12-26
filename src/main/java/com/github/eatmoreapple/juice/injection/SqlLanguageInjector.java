package com.github.eatmoreapple.juice.injection;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class SqlLanguageInjector implements MultiHostInjector {
    private static final Set<String> SQL_TAGS = Set.of("insert", "select", "update", "delete", "sql");
    private static final Language SQL_LANGUAGE = Language.findLanguageByID("SQL");

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof XmlText xmlText)) {
            return;
        }

        // 快速检查：如果文本为空，直接返回
        String text = xmlText.getText();
        if (text.isBlank()) {
            return;
        }

        // 检查是否在SQL标签内部
        if (!isInsideSqlTag(xmlText)) {
            return;
        }

        // Inject SQL language
        if (SQL_LANGUAGE != null) {
            registrar
                .startInjecting(SQL_LANGUAGE)
                .addPlace(null, null, (PsiLanguageInjectionHost) context, 
                         new TextRange(0, text.length()))
                .doneInjecting();
        }
    }

    private boolean isInsideSqlTag(@NotNull XmlText xmlText) {
        XmlTag currentTag = xmlText.getParentTag();
        if (currentTag == null) {
            return false;
        }
        
        // 获取最近的SQL标签
        while (currentTag != null) {
            if (SQL_TAGS.contains(currentTag.getName())) {
                return true;
            }
            currentTag = currentTag.getParentTag();
        }
        return false;
    }

    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(XmlText.class);
    }
}
