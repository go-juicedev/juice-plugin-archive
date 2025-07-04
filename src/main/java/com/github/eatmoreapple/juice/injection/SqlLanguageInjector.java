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

import java.util.List;
import java.util.Set;

/**
 * SQL 语言注入器
 * 在 MyBatis XML 文件中的 SQL 标签内注入 SQL 语言
 */
public class SqlLanguageInjector implements MultiHostInjector {
    // SQL 标签集合
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
        
        // 检查是否正在输入 XML 标签，如果是则不注入 SQL 语言
        if (isTypingXmlTag(text)) {
            return;
        }

        // 注入 SQL 语言
        if (SQL_LANGUAGE != null) {
            registrar
                .startInjecting(SQL_LANGUAGE)
                .addPlace(null, null, (PsiLanguageInjectionHost) context, 
                         new TextRange(0, text.length()))
                .doneInjecting();
        }
    }

    /**
     * 检查是否正在输入 XML 标签
     * 当用户正在输入标签时，我们不应该注入 SQL 语言
     * 以便能正确地进行标签补全
     */
    private boolean isTypingXmlTag(String text) {
        String trimmed = text.trim();
        // 检查是否正在输入标签开始符号
        return trimmed.endsWith("<") || 
               // foreach 标签前缀
               trimmed.endsWith("<f") || 
               trimmed.endsWith("<fo") || 
               trimmed.endsWith("<for") || 
               trimmed.endsWith("<fore") || 
               trimmed.endsWith("<forea") || 
               trimmed.endsWith("<foreac") ||
               // if 标签前缀
               trimmed.endsWith("<i") ||
               trimmed.endsWith("<if") ||
               // choose 标签前缀
               trimmed.endsWith("<c") ||
               trimmed.endsWith("<ch") ||
               trimmed.endsWith("<cho") ||
               trimmed.endsWith("<choo") ||
               trimmed.endsWith("<choos") ||
               trimmed.endsWith("<choose") ||
               // where 标签前缀
               trimmed.endsWith("<w") ||
               trimmed.endsWith("<wh") ||
               trimmed.endsWith("<whe") ||
               trimmed.endsWith("<wher") ||
               trimmed.endsWith("<where");
    }

    /**
     * 检查是否在 SQL 标签内部
     */
    private boolean isInsideSqlTag(@NotNull XmlText xmlText) {
        XmlTag currentTag = xmlText.getParentTag();
        if (currentTag == null) {
            return false;
        }
        
        // 获取最近的SQL标签
        while (currentTag != null) {
            String tagName = currentTag.getName();
            if (SQL_TAGS.contains(tagName)) {
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
