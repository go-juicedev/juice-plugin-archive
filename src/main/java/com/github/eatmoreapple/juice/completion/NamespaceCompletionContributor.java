package com.github.eatmoreapple.juice.completion;

import com.goide.psi.GoFile;
import com.goide.psi.GoInterfaceType;
import com.goide.psi.GoTypeSpec;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class NamespaceCompletionContributor extends CompletionContributor {
    private static final Logger log = Logger.getInstance(NamespaceCompletionContributor.class);

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
                        PsiElement position = parameters.getPosition();
                        Project project = position.getProject();

                        try {
                            // 获取模块名
                            String moduleName = getModuleName(project);
                            if (moduleName == null) {
                                return;
                            }

                            // 获取当前输入的文本
                            String currentText = parameters.getPosition().getText();
                            currentText = currentText.replaceAll("[\"']", "").trim();
                            
                            // 处理 IntellijIdeaRulezzz 标记
                            if (currentText.endsWith("IntellijIdeaRulezzz")) {
                                currentText = currentText.substring(0, currentText.length() - "IntellijIdeaRulezzz".length());
                            }

                            // 如果输入为空，提供模块名补全
                            if (currentText.isEmpty()) {
                                result.addElement(LookupElementBuilder.create(moduleName + ".")
                                        .withPresentableText(moduleName + ".")
                                        .withTypeText("Module")
                                        .withBoldness(true));
                                return;
                            }

                            // 去掉模块名部分
                            String remainingPath = currentText;
                            if (remainingPath.startsWith(moduleName)) {
                                remainingPath = remainingPath.substring(moduleName.length());
                                if (remainingPath.startsWith(".")) {
                                    remainingPath = remainingPath.substring(1);
                                }
                            }

                            // 获取项目根目录
                            VirtualFile baseDir = project.getBaseDir();
                            if (baseDir == null) {
                                return;
                            }

                            // 构建当前路径
                            VirtualFile currentDir = baseDir;
                            if (!remainingPath.isEmpty()) {
                                String[] pathParts = remainingPath.split("\\.");
                                for (String part : pathParts) {
                                    if (!part.isEmpty()) {
                                        VirtualFile child = currentDir.findChild(part);
                                        if (child != null && child.isDirectory()) {
                                            currentDir = child;
                                        } else {
                                            return;
                                        }
                                    }
                                }
                            }

                            // 获取当前目录下的所有子目录和 Go 文件
                            Set<String> suggestions = new HashSet<>();

                            // 添加子目录
                            for (VirtualFile child : currentDir.getChildren()) {
                                if (child.isDirectory() && !child.getName().startsWith(".")) {
                                    String suggestion = getRelativePath(baseDir, child);
                                    suggestions.add(suggestion);
                                }
                            }

                            // 添加当前目录下的接口
                            for (VirtualFile child : currentDir.getChildren()) {
                                if (!child.isDirectory() && "go".equals(child.getExtension())) {
                                    PsiFile psiFile = PsiManager.getInstance(project).findFile(child);
                                    if (psiFile instanceof GoFile) {
                                        GoFile goFile = (GoFile) psiFile;
                                        for (GoTypeSpec typeSpec : goFile.getTypes()) {
                                            String typeName = typeSpec.getName();
                                            // 检查类型名是否以 Repository 结尾
                                            if (typeName != null && typeName.endsWith("Repository")) {
                                                String suggestion = getRelativePath(baseDir, child.getParent());
                                                // 如果不是基目录，添加点号
                                                if (!suggestion.isEmpty()) {
                                                    suggestion += ".";
                                                }
                                                suggestion += typeName;
                                                suggestions.add(suggestion);
                                            }
                                        }
                                    }
                                }
                            }

                            // 添加补全建议
                            for (String suggestion : suggestions) {
                                // 获取显示文本（最后一个点号后的内容）
                                String displayText = suggestion;
                                int lastDot = suggestion.lastIndexOf(".");
                                if (lastDot != -1) {
                                    displayText = suggestion.substring(lastDot + 1);
                                }
                                
                                // 构建完整建议
                                String fullSuggestion = moduleName + "." + suggestion;
                                
                                // 如果当前输入以点号结尾，只显示当前级别
                                if (currentText.endsWith(".")) {
                                    result.addElement(LookupElementBuilder.create(fullSuggestion)
                                            .withPresentableText(displayText)
                                            .withTypeText(suggestion.contains(".") ? "Package" : "Interface"));
                                } else {
                                    // 否则显示完整路径
                                    result.addElement(LookupElementBuilder.create(fullSuggestion + ".")
                                            .withPresentableText(displayText + ".")
                                            .withTypeText(suggestion.contains(".") ? "Package" : "Interface")
                                            .withInsertHandler((insertContext, item) -> {
                                                // 如果插入的是包路径（以.结尾），自动触发下一级的补全
                                                if (item.getLookupString().endsWith(".")) {
                                                    insertContext.setLaterRunnable(() -> {
                                                        Editor editor = insertContext.getEditor();
                                                        new CodeCompletionHandlerBase(CompletionType.BASIC)
                                                            .invokeCompletion(project, editor);
                                                        editor.getCaretModel().moveToOffset(editor.getCaretModel().getOffset());
                                                    });
                                                }
                                            }));
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Error in namespace completion", e);
                        }
                    }
                });
    }

    /**
     * 获取相对路径
     */
    private String getRelativePath(VirtualFile baseDir, VirtualFile file) {
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

    /**
     * 获取模块名
     */
    private String getModuleName(Project project) {
        try {
            // 获取项目的根目录
            String basePath = project.getBasePath();
            if (basePath == null) {
                return null;
            }

            // 找到 go.mod 文件
            VirtualFile goModFile = project.getBaseDir().findChild("go.mod");
            if (goModFile == null || !goModFile.exists()) {
                return null;
            }

            // 读取 go.mod 文件内容
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(goModFile.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("module ")) {
                        String moduleName = line.substring("module ".length()).trim();
                        return moduleName;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read go.mod file", e);
        }
        return null;
    }
}