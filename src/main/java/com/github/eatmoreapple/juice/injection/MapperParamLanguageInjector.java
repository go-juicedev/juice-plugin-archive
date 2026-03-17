package com.github.eatmoreapple.juice.injection;

import com.github.eatmoreapple.juice.lang.MapperParamLanguage;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapperParamLanguageInjector implements MultiHostInjector {
    private static final Set<String> SQL_TAGS = Set.of("insert", "select", "update", "delete", "sql");
    private static final Pattern PARAM_PATTERN = Pattern.compile("#\\{([^}]+)\\}|\\$\\{([^}]+)\\}");

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof XmlText xmlText)) {
            return;
        }

        if (!isInsideSqlTag(xmlText)) {
            return;
        }

        String text = xmlText.getText();
        if (text.isBlank()) {
            return;
        }

        Matcher matcher = PARAM_PATTERN.matcher(text);
        boolean started = false;
        while (matcher.find()) {
            if (!started) {
                registrar.startInjecting(MapperParamLanguage.INSTANCE);
                started = true;
            }
            registrar.addPlace(null, null, (PsiLanguageInjectionHost) context, new TextRange(matcher.start(), matcher.end()));
        }
        if (started) {
            registrar.doneInjecting();
        }
    }

    @Override
    public @NotNull List<Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(XmlText.class);
    }

    private boolean isInsideSqlTag(@NotNull XmlText xmlText) {
        XmlTag currentTag = xmlText.getParentTag();
        while (currentTag != null) {
            if (SQL_TAGS.contains(currentTag.getName())) {
                return true;
            }
            currentTag = currentTag.getParentTag();
        }
        return false;
    }
}
