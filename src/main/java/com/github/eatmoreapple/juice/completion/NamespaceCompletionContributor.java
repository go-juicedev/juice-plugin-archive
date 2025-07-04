package com.github.eatmoreapple.juice.completion;

import com.goide.psi.GoFile;
import com.goide.psi.GoTypeSpec;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
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
                            
                            // 获取模块名
                            String moduleName = ModuleUtils.getModuleName(project);
                            if (moduleName == null) {
                                LOG.debug("Module name is null, skipping namespace completion");
                                return;
                            }

                            // 处理当前输入文本
                            String currentText = cleanInputText(position.getText());

                            // 处理空输入或只有点号的情况
                            if (currentText.isEmpty() || DOT.equals(currentText)) {
                                addModuleNameCompletion(result, parameters, moduleName, project);
                                return;
                            }

                            // 解析路径并获取当前目录
                            VirtualFile currentDir = resolveDirectory(project, moduleName, currentText);
                            if (currentDir == null) {
                                return;
                            }

                            // 收集补全建议
                            Set<String> suggestions = collectSuggestions(project, currentDir);

                            // 添加补全建议
                            addSuggestionsToResult(result, suggestions, currentText, project);
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
     * 添加模块名补全
     */
    private void addModuleNameCompletion(@NotNull CompletionResultSet result, 
                                       @NotNull CompletionParameters parameters, 
                                       @NotNull String moduleName,
                                       @NotNull Project project) {
        result.addElement(LookupElementBuilder.create(moduleName)
                .withPresentableText(moduleName)
                .withTypeText("Module")
                .withInsertHandler((insertContext, item) -> {
                    // 处理点号开头的情况
                    handleLeadingDot(insertContext, parameters);
                    
                    // 自动触发下一级补全
                    triggerNextLevelCompletion(insertContext, project);
                }));
    }

    /**
     * 处理输入文本开头的点号
     */
    private void handleLeadingDot(@NotNull InsertionContext insertContext, @NotNull CompletionParameters parameters) {
        if (parameters.getPosition().getText().startsWith(DOT)) {
            Editor editor = insertContext.getEditor();
            Document document = editor.getDocument();
            int startOffset = insertContext.getStartOffset() - 1;
            if (startOffset >= 0) {
                document.deleteString(startOffset, startOffset + 1);
            }
        }
    }

    /**
     * 触发下一级补全
     */
    private void triggerNextLevelCompletion(@NotNull InsertionContext insertContext, @NotNull Project project) {
        insertContext.setLaterRunnable(() -> {
            Editor editor = insertContext.getEditor();
            new CodeCompletionHandlerBase(CompletionType.BASIC)
                .invokeCompletion(project, editor);
            editor.getCaretModel().moveToOffset(editor.getCaretModel().getOffset());
        });
    }

    /**
     * 解析目录路径
     */
    @Nullable
    private VirtualFile resolveDirectory(@NotNull Project project, @NotNull String moduleName, @NotNull String currentText) {
        // 获取项目根目录
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return null;
        }

        // 去掉模块名部分
        String remainingPath = stripModuleName(currentText, moduleName);
        if (remainingPath.isEmpty()) {
            return baseDir;
        }

        // 按点号分割路径并导航到目标目录
        return navigateToTargetDirectory(baseDir, remainingPath);
    }

    /**
     * 去除模块名前缀
     */
    @NotNull
    private String stripModuleName(@NotNull String path, @NotNull String moduleName) {
        if (path.startsWith(moduleName)) {
            String remaining = path.substring(moduleName.length());
            return remaining.startsWith(DOT) ? remaining.substring(1) : remaining;
        }
        return path;
    }

    /**
     * 导航到目标目录
     */
    @Nullable
    private VirtualFile navigateToTargetDirectory(@NotNull VirtualFile baseDir, @NotNull String path) {
        VirtualFile currentDir = baseDir;
        String[] pathParts = path.split("\\.");
        
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
    private Set<String> collectSuggestions(@NotNull Project project, @NotNull VirtualFile currentDir) {
        Set<String> suggestions = new HashSet<>();
        
        // 添加子目录
        collectDirectorySuggestions(currentDir, suggestions);
        
        // 添加接口
        collectInterfaceSuggestions(project, currentDir, suggestions);
        
        return suggestions;
    }

    /**
     * 收集目录建议
     */
    private void collectDirectorySuggestions(@NotNull VirtualFile currentDir, @NotNull Set<String> suggestions) {
        for (VirtualFile child : currentDir.getChildren()) {
            if (child.isDirectory() && !child.getName().startsWith(DOT)) {
                suggestions.add(child.getName());
            }
        }
    }

    /**
     * 收集接口建议
     */
    private void collectInterfaceSuggestions(@NotNull Project project, @NotNull VirtualFile currentDir, @NotNull Set<String> suggestions) {
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
     * 处理Go文件，提取所有类型
     */
    private void processGoFile(@NotNull GoFile goFile, @NotNull Set<String> suggestions) {
        for (GoTypeSpec typeSpec : goFile.getTypes()) {
            // 添加所有类型，不再特定过滤接口
            String typeName = typeSpec.getName();
            if (typeName != null) {
                suggestions.add(typeName);
            }
        }
    }

    /**
     * 添加补全建议到结果集
     */
    private void addSuggestionsToResult(@NotNull CompletionResultSet result, 
                                      @NotNull Set<String> suggestions, 
                                      @NotNull String currentText,
                                      @NotNull Project project) {
        boolean needsDot = !currentText.endsWith(DOT);
        
        for (String suggestion : suggestions) {
            String[] parts = suggestion.split("\\.");
            String lastPart = parts[parts.length - 1];
            
            LookupElement element = createLookupElement(lastPart, needsDot, project);
            result.addElement(element);
        }
    }

    /**
     * 创建补全元素
     */
    @NotNull
    private LookupElement createLookupElement(@NotNull String text, boolean needsDot, @NotNull Project project) {
        return LookupElementBuilder.create(text)
                .withPresentableText(text)
                .withTypeText("Package")
                .withInsertHandler((insertContext, item) -> {
                    if (needsDot) {
                        // 插入点号并触发下一级补全
                        insertDotAndTriggerCompletion(insertContext, project);
                    }
                });
    }

    /**
     * 插入点号并触发下一级补全
     */
    private void insertDotAndTriggerCompletion(@NotNull InsertionContext insertContext, @NotNull Project project) {
        Editor editor = insertContext.getEditor();
        Document document = editor.getDocument();
        document.insertString(insertContext.getTailOffset(), DOT);
        editor.getCaretModel().moveToOffset(insertContext.getTailOffset());
        
        new CodeCompletionHandlerBase(CompletionType.BASIC)
            .invokeCompletion(project, editor);
    }

    /**
     * 获取相对路径
     */
    @NotNull
    private String getRelativePath(@NotNull VirtualFile baseDir, @NotNull VirtualFile file) {
        String basePath = baseDir.getPath();
        String filePath = file.getPath();
        
        if (filePath.startsWith(basePath)) {
            String relativePath = filePath.substring(basePath.length());
            // 移除开头的斜杠
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            // 将斜杠替换为点号
            return relativePath.replace('/', '.');
        }
        return "";
    }
}