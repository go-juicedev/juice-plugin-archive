package com.github.eatmoreapple.juice.injection;

import com.github.eatmoreapple.juice.lang.MapperParamLanguage;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class MapperParamLanguageInjector implements MultiHostInjector {
    private static final Logger LOG = Logger.getInstance(MapperParamLanguageInjector.class);
    private static final boolean DEBUG = Boolean.getBoolean("juice.debug.injection");
    private static final Set<String> SQL_TAGS = Set.of("insert", "select", "update", "delete", "sql");

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

        List<TextRange> ranges = MapperParamSupport.findParamRanges(text);
        boolean started = false;
        for (TextRange range : ranges) {
            if (!started) {
                registrar.startInjecting(MapperParamLanguage.INSTANCE);
                started = true;
            }
            registrar.addPlace(null, null, (PsiLanguageInjectionHost) context, range);
        }
        if (started) {
            registrar.doneInjecting();
            debug("Injected MapperParam into " + ranges.size() + " range(s): " + summarize(text));
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

    private void debug(@NotNull String message) {
        if (DEBUG) {
            LOG.warn(message);
        }
    }

    private @NotNull String summarize(@NotNull String text) {
        String normalized = text.replace('\n', ' ').trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 117) + "...";
    }
}
