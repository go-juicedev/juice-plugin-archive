package com.github.eatmoreapple.juice.injection;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared placeholder parsing logic used by both SQL and MapperParam injection.
 */
public final class MapperParamSupport {
    private static final Pattern PARAM_PATTERN = Pattern.compile("#\\{([^}]+)\\}|\\$\\{([^}]+)\\}");

    private MapperParamSupport() {
    }

    public static @NotNull List<TextRange> findParamRanges(@NotNull String text) {
        List<TextRange> ranges = new ArrayList<>();
        Matcher matcher = PARAM_PATTERN.matcher(text);
        while (matcher.find()) {
            ranges.add(new TextRange(matcher.start(), matcher.end()));
        }
        return ranges;
    }

    public static @NotNull List<SqlFragment> buildSqlFragments(@NotNull String text) {
        List<SqlFragment> fragments = new ArrayList<>();
        Matcher matcher = PARAM_PATTERN.matcher(text);
        int lastOffset = 0;
        String pendingPrefix = null;
        boolean foundParam = false;

        while (matcher.find()) {
            foundParam = true;
            String replacement = sqlReplacementForParam(matcher.group());
            if (lastOffset < matcher.start()) {
                fragments.add(new SqlFragment(new TextRange(lastOffset, matcher.start()), pendingPrefix, replacement));
                pendingPrefix = null;
            } else {
                pendingPrefix = appendReplacement(pendingPrefix, replacement);
            }
            lastOffset = matcher.end();
        }

        if (lastOffset < text.length()) {
            fragments.add(new SqlFragment(new TextRange(lastOffset, text.length()), pendingPrefix, null));
        } else if (!foundParam) {
            fragments.add(new SqlFragment(new TextRange(0, text.length()), null, null));
        }

        return fragments;
    }

    public record SqlFragment(@NotNull TextRange range, String prefix, String suffix) {
    }

    private static @NotNull String sqlReplacementForParam(@NotNull String placeholder) {
        if (placeholder.startsWith("#{")) {
            return " ? ";
        }
        return " juice_param ";
    }

    private static @NotNull String appendReplacement(String existing, @NotNull String replacement) {
        if (existing == null || existing.isEmpty()) {
            return replacement;
        }
        return existing + replacement;
    }
}
