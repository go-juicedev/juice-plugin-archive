package com.github.eatmoreapple.juice.completion;

import com.goide.psi.GoFile;
import com.goide.psi.GoTypeSpec;
import com.goide.stubs.index.GoTypesIndex;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.icons.AllIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.github.eatmoreapple.juice.util.ModuleUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 命名空间补全贡献器
 * 为 mapper 标签的 namespace 属性提供补全功能
 */
public class NamespaceCompletionContributor extends CompletionContributor {
    private static final Logger LOG = Logger.getInstance(NamespaceCompletionContributor.class);
    private static final Pattern INTELLIJ_MARKER_PATTERN = Pattern.compile("IntellijIdeaRulezzz");
    private static final String DOT = ".";
    private static final String MAIN_PACKAGE = "main";

    public NamespaceCompletionContributor() {
        // 为 mapper 标签的 namespace 属性添加补全
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement()
                        .inside(XmlPatterns.xmlAttribute("namespace")
                                .withParent(XmlPatterns.xmlTag().withName("mapper"))),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                @NotNull ProcessingContext context,
                                                @NotNull CompletionResultSet result) {
                        try {
                            PsiElement position = parameters.getPosition();
                            Project project = position.getProject();
                            
                            // 处理当前输入文本
                            String currentText = getCurrentNamespacePrefix(parameters);

                            // 获取模块名
                            String moduleName = ModuleUtils.getModuleName(project);
                            
                            // 处理空输入或只有点号的情况
                            if (currentText.isEmpty() || DOT.equals(currentText)) {
                                addNamespaceRootCompletions(result, moduleName, project, "");
                                return;
                            }

                            CompletionContext completionContext = parseCompletionContext(currentText, moduleName);
                            if (completionContext == null) {
                                addNamespaceRootCompletions(result, moduleName, project, currentText);
                                return;
                            }

                            // 收集补全建议
                            Set<CompletionSuggestion> suggestions = collectSuggestions(project, completionContext);

                            // 添加补全建议
                            addSuggestionsToResult(
                                    result.withPrefixMatcher(completionContext.currentSegment()),
                                    suggestions,
                                    project,
                                    completionContext.insertLeadingDot());
                        } catch (Exception e) {
                            LOG.warn("Error in namespace completion", e);
                        }
                    }
                });
    }

    /**
     * 清理输入文本，移除引号和IntelliJ标记
     */
    @NotNull
    private String cleanInputText(@NotNull String text) {
        // 移除引号
        String cleaned = text.replaceAll("[\"']", "").trim();
        
        // 移除IntelliJ标记
        return INTELLIJ_MARKER_PATTERN.matcher(cleaned).replaceAll("");
    }

    /**
     * 获取 namespace 属性中光标前的文本。
     */
    @NotNull
    private String getCurrentNamespacePrefix(@NotNull CompletionParameters parameters) {
        PsiElement position = parameters.getPosition();
        XmlAttributeValue value = PsiTreeUtil.getParentOfType(position, XmlAttributeValue.class, false);
        if (value == null && parameters.getOriginalPosition() != null) {
            value = PsiTreeUtil.getParentOfType(parameters.getOriginalPosition(), XmlAttributeValue.class, false);
        }
        if (value == null) {
            return cleanInputText(position.getText());
        }

        TextRange valueRange = value.getValueTextRange();
        int valueStart = valueRange.getStartOffset();
        int caretOffset = Math.max(valueStart, Math.min(parameters.getOffset(), valueRange.getEndOffset()));
        Document document = parameters.getEditor().getDocument();
        if (caretOffset <= document.getTextLength()) {
            return cleanInputText(document.getText(TextRange.create(valueStart, caretOffset)));
        }

        return cleanInputText(value.getValue());
    }

    /**
     * 添加 namespace 根节点补全。
     */
    private void addNamespaceRootCompletions(@NotNull CompletionResultSet result,
                                             @Nullable String moduleName,
                                             @NotNull Project project,
                                             @NotNull String prefix) {
        CompletionResultSet prefixedResult = result.withPrefixMatcher(prefix);
        if (moduleName != null) {
            addModuleNameCompletion(prefixedResult, moduleName, project, "Module");
        }
        addModuleNameCompletion(prefixedResult, MAIN_PACKAGE, project, "Package");
    }

    /**
     * 添加模块名或 main 包补全
     */
    private void addModuleNameCompletion(@NotNull CompletionResultSet result, 
                                       @NotNull String name,
                                       @NotNull Project project,
                                       @NotNull String typeText) {
        result.addElement(LookupElementBuilder.create(name)
                .withPresentableText(name)
                .withTypeText(typeText)
                .withInsertHandler((insertContext, item) -> {
                    insertDotAndTriggerCompletion(insertContext, project);
                }));
    }

    /**
     * 解析 namespace 补全上下文。
     */
    @Nullable
    private CompletionContext parseCompletionContext(@NotNull String currentText, @Nullable String moduleName) {
        if (currentText.startsWith(MAIN_PACKAGE + DOT)) {
            String remainder = currentText.substring((MAIN_PACKAGE + DOT).length());
            return CompletionContext.main(currentSegment(remainder), false);
        }

        if (currentText.equals(MAIN_PACKAGE)) {
            return CompletionContext.main("", true);
        }

        if (moduleName == null) {
            return null;
        }

        if (currentText.equals(moduleName)) {
            return CompletionContext.module("", "", true);
        }

        if (!currentText.startsWith(moduleName + DOT)) {
            return null;
        }

        String remainder = currentText.substring((moduleName + DOT).length());
        int lastDot = remainder.lastIndexOf(DOT);
        if (lastDot < 0) {
            return CompletionContext.module("", remainder, false);
        }

        return CompletionContext.module(
                remainder.substring(0, lastDot).replace('.', '/'),
                remainder.substring(lastDot + 1),
                false);
    }

    @NotNull
    private String currentSegment(@NotNull String path) {
        int lastDot = path.lastIndexOf(DOT);
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }

    /**
     * 解析目录路径
     */
    @Nullable
    private VirtualFile resolveDirectory(@NotNull Project project, @NotNull String relativeDirPath) {
        // 获取项目根目录
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return null;
        }

        if (relativeDirPath.isEmpty()) {
            return baseDir;
        }

        return navigateToTargetDirectory(baseDir, relativeDirPath);
    }

    /**
     * 导航到目标目录
     */
    @Nullable
    private VirtualFile navigateToTargetDirectory(@NotNull VirtualFile baseDir, @NotNull String path) {
        VirtualFile currentDir = baseDir;
        String[] pathParts = path.split("/");
        
        for (String part : pathParts) {
            if (!part.isEmpty()) {
                VirtualFile child = currentDir.findChild(part);
                if (child != null && child.isDirectory()) {
                    currentDir = child;
                } else {
                    return null;
                }
            }
        }
        
        return currentDir;
    }

    /**
     * 收集补全建议
     */
    @NotNull
    private Set<CompletionSuggestion> collectSuggestions(@NotNull Project project,
                                                         @NotNull CompletionContext completionContext) {
        Set<CompletionSuggestion> suggestions = new HashSet<>();

        if (completionContext.mainPackage()) {
            collectMainPackageInterfaceSuggestions(project, suggestions);
            return suggestions;
        }

        VirtualFile currentDir = resolveDirectory(project, completionContext.relativeDirPath());
        if (currentDir == null) {
            return suggestions;
        }

        // 添加子目录
        collectDirectorySuggestions(currentDir, suggestions);

        // 添加接口
        collectInterfaceSuggestions(project, currentDir, suggestions);
        
        return suggestions;
    }

    /**
     * 补全上下文。
     */
    private record CompletionContext(
            boolean mainPackage,
            @NotNull String relativeDirPath,
            @NotNull String currentSegment,
            boolean insertLeadingDot
    ) {
        static CompletionContext main(@NotNull String currentSegment, boolean insertLeadingDot) {
            return new CompletionContext(true, "", currentSegment, insertLeadingDot);
        }

        static CompletionContext module(@NotNull String relativeDirPath,
                                        @NotNull String currentSegment,
                                        boolean insertLeadingDot) {
            return new CompletionContext(false, relativeDirPath, currentSegment, insertLeadingDot);
        }
    }

    /**
     * 补全建议类型
     */
    private static class CompletionSuggestion {
        private final String name;
        private final SuggestionType type;
        
        public CompletionSuggestion(String name, SuggestionType type) {
            this.name = name;
            this.type = type;
        }
        
        public String getName() { return name; }
        public SuggestionType getType() { return type; }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CompletionSuggestion that = (CompletionSuggestion) o;
            return name.equals(that.name) && type == that.type;
        }
        
        @Override
        public int hashCode() {
            return name.hashCode() * 31 + type.hashCode();
        }
    }
    
    /**
     * 建议类型枚举
     */
    private enum SuggestionType {
        DIRECTORY, INTERFACE
    }

    /**
     * 收集目录建议
     */
    private void collectDirectorySuggestions(@NotNull VirtualFile currentDir, @NotNull Set<CompletionSuggestion> suggestions) {
        for (VirtualFile child : currentDir.getChildren()) {
            if (child.isDirectory() && !child.getName().startsWith(DOT)) {
                suggestions.add(new CompletionSuggestion(child.getName(), SuggestionType.DIRECTORY));
            }
        }
    }

    /**
     * 收集接口建议
     */
    private void collectInterfaceSuggestions(@NotNull Project project, @NotNull VirtualFile currentDir, @NotNull Set<CompletionSuggestion> suggestions) {
        PsiManager psiManager = PsiManager.getInstance(project);
        
        for (VirtualFile child : currentDir.getChildren()) {
            if (!child.isDirectory() && "go".equals(child.getExtension())) {
                PsiFile psiFile = psiManager.findFile(child);
                if (psiFile instanceof GoFile) {
                    processGoFile((GoFile) psiFile, suggestions);
                }
            }
        }
    }

    /**
     * 收集 main 包中的接口建议。
     */
    private void collectMainPackageInterfaceSuggestions(@NotNull Project project,
                                                       @NotNull Set<CompletionSuggestion> suggestions) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        for (String key : StubIndex.getInstance().getAllKeys(GoTypesIndex.KEY, project)) {
            for (GoTypeSpec typeSpec : StubIndex.getElements(GoTypesIndex.KEY, key, project, scope, GoTypeSpec.class)) {
                PsiFile file = typeSpec.getContainingFile();
                if (!(file instanceof GoFile goFile) || !MAIN_PACKAGE.equals(goFile.getPackageName())) {
                    continue;
                }
                if (isInterfaceType(typeSpec) && typeSpec.getName() != null) {
                    suggestions.add(new CompletionSuggestion(typeSpec.getName(), SuggestionType.INTERFACE));
                }
            }
        }
    }

    /**
     * 处理Go文件，仅提取接口类型
     */
    private void processGoFile(@NotNull GoFile goFile, @NotNull Set<CompletionSuggestion> suggestions) {
        for (GoTypeSpec typeSpec : goFile.getTypes()) {
            // 通过检查是否有方法来判断是否为接口
            if (isInterfaceType(typeSpec)) {
                String typeName = typeSpec.getName();
                if (typeName != null) {
                    suggestions.add(new CompletionSuggestion(typeName, SuggestionType.INTERFACE));
                }
            }
        }
    }

    /**
     * 判断是否为接口类型
     */
    private boolean isInterfaceType(@NotNull GoTypeSpec typeSpec) {
        // 检查类型定义的文本内容，接口类型会包含 "interface" 关键字
        String text = typeSpec.getText();
        if (text == null) return false;
        
        // 简单的文本匹配，检查是否包含 "interface {" 模式
        return text.contains("interface") && text.contains("{") && 
               text.indexOf("interface") < text.indexOf("{");
    }

    /**
     * 添加补全建议到结果集
     */
    private void addSuggestionsToResult(@NotNull CompletionResultSet result, 
                                      @NotNull Set<CompletionSuggestion> suggestions, 
                                      @NotNull Project project,
                                      boolean insertLeadingDot) {
        for (CompletionSuggestion suggestion : suggestions) {
            String name = suggestion.getName();
            SuggestionType type = suggestion.getType();
            
            String[] parts = name.split("\\.");
            String lastPart = parts[parts.length - 1];
            
            LookupElement element = createLookupElement(lastPart, type, project, insertLeadingDot);
            result.addElement(element);
        }
    }

    /**
     * 创建补全元素
     */
    @NotNull
    private LookupElement createLookupElement(@NotNull String text, @NotNull SuggestionType type, 
                                            @NotNull Project project,
                                            boolean insertLeadingDot) {
        LookupElementBuilder builder = LookupElementBuilder.create(text)
                .withPresentableText(text);
        
        // 根据类型设置不同的外观
        if (type == SuggestionType.DIRECTORY) {
            builder = builder
                    .withIcon(AllIcons.Nodes.Folder)
                    .withTypeText("Directory")
                    .withItemTextForeground(JBColor.BLUE)
                    .withInsertHandler((insertContext, item) -> {
                        insertLeadingDotIfNeeded(insertContext, insertLeadingDot);
                        insertDotAndTriggerCompletion(insertContext, project);
                    });
        } else if (type == SuggestionType.INTERFACE) {
            builder = builder
                    .withIcon(AllIcons.Nodes.Interface)
                    .withTypeText("Interface")
                    .withItemTextForeground(JBColor.MAGENTA)
                    .withBoldness(true)
                    .withInsertHandler((insertContext, item) -> {
                        insertLeadingDotIfNeeded(insertContext, insertLeadingDot);
                    });
        }
        
        return builder;
    }

    /**
     * 当前值刚好是 module/main 时，下一级补全需要先插入路径分隔点。
     */
    private void insertLeadingDotIfNeeded(@NotNull InsertionContext insertContext, boolean insertLeadingDot) {
        if (!insertLeadingDot) {
            return;
        }

        Editor editor = insertContext.getEditor();
        Document document = editor.getDocument();
        int startOffset = insertContext.getStartOffset();
        if (startOffset <= 0 || startOffset > document.getTextLength()) {
            return;
        }

        CharSequence chars = document.getCharsSequence();
        if (chars.charAt(startOffset - 1) == '.') {
            return;
        }

        document.insertString(startOffset, DOT);
        insertContext.setTailOffset(insertContext.getTailOffset() + 1);
        editor.getCaretModel().moveToOffset(insertContext.getTailOffset());
    }

    /**
     * 插入点号并触发下一级补全
     */
    private void insertDotAndTriggerCompletion(@NotNull InsertionContext insertContext, @NotNull Project project) {
        Editor editor = insertContext.getEditor();
        Document document = editor.getDocument();
        
        // 1. 只处理属性值开头的点号，不能删除 a.b 中间的分隔点。
        if (shouldDeleteLeadingDot(insertContext)) {
            int startOffset = insertContext.getStartOffset();
            document.deleteString(startOffset - 1, startOffset);
            insertContext.setTailOffset(insertContext.getTailOffset() - 1);
        }

        // 2. 处理尾部点号
        int tailOffset = insertContext.getTailOffset();
        
        // 检查当前位置后面是否已经有点号
        boolean alreadyHasDot = false;
        if (tailOffset < document.getTextLength()) {
            char nextChar = document.getCharsSequence().charAt(tailOffset);
            if (nextChar == '.') {
                alreadyHasDot = true;
            }
        }
        
        if (!alreadyHasDot) {
            document.insertString(tailOffset, DOT);
            editor.getCaretModel().moveToOffset(tailOffset + 1);
        } else {
            // 如果已经有点号，只需移动光标到点号后面
            editor.getCaretModel().moveToOffset(tailOffset + 1);
        }
        
        // 自动触发下次补全
        insertContext.setLaterRunnable(() -> {
            new CodeCompletionHandlerBase(CompletionType.BASIC)
                .invokeCompletion(project, editor);
        });
    }

    private boolean shouldDeleteLeadingDot(@NotNull InsertionContext insertContext) {
        Document document = insertContext.getDocument();
        int startOffset = insertContext.getStartOffset();
        if (startOffset <= 0 || startOffset > document.getTextLength()) {
            return false;
        }

        CharSequence chars = document.getCharsSequence();
        int dotOffset = startOffset - 1;
        if (chars.charAt(dotOffset) != '.') {
            return false;
        }

        return dotOffset == findAttributeValueStartOffset(chars, dotOffset);
    }

    private int findAttributeValueStartOffset(@NotNull CharSequence chars, int beforeOffset) {
        for (int i = beforeOffset - 1; i >= 0; i--) {
            char c = chars.charAt(i);
            if (c == '"' || c == '\'') {
                return i + 1;
            }
        }
        return -1;
    }
}
