package com.github.eatmoreapple.juice.injection;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapperParamLanguageInjector implements MultiHostInjector {
    
    private static final Pattern PARAM_PATTERN = Pattern.compile("#\\{([^}]+)\\}|\\$\\{([^}]+)\\}");

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof XmlText)) {
            return;
        }
        
        XmlText xmlText = (XmlText) context;
        String text = xmlText.getText();
        
        Matcher matcher = PARAM_PATTERN.matcher(text);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            
            registrar.startInjecting(Language.findLanguageByID("TEXT"))
                    .addPlace(null, null, (PsiLanguageInjectionHost) context, new TextRange(start, end))
                    .doneInjecting();
        }
    }

    @Override
    public @NotNull List<Class<? extends PsiElement>> elementsToInjectIn() {
        return Collections.singletonList(XmlText.class);
    }
}