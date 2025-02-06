package com.github.eatmoreapple.juice.completion;

import com.goide.psi.GoFile;
import com.goide.psi.GoInterfaceType;
import com.goide.psi.GoTypeSpec;
import com.intellij.codeInsight.completion.*;
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
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import com.github.eatmoreapple.juice.util.ModuleUtils;

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
                            String moduleName = ModuleUtils.getModuleName(project);
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

                            // 如果输入为空或只有一个点号，提供模块名补全
                            if (currentText.isEmpty() || currentText.equals(".")) {
                                result.addElement(LookupElementBuilder.create(moduleName)
                                        .withInsertHandler((insertContext, item) -> {
                                            // 如果原始输入是点号开头，需要删除这个点号
                                            if (parameters.getPosition().getText().startsWith(".")) {
                                                Editor editor = insertContext.getEditor();
                                                Document document = editor.getDocument();
                                                int startOffset = insertContext.getStartOffset() - 1;
                                                if (startOffset >= 0) {
                                                    document.deleteString(startOffset, startOffset + 1);
                                                }
                                            }
                                            // 自动触发下一级补全
                                            insertContext.setLaterRunnable(() -> {
                                                Editor editor = insertContext.getEditor();
                                                new CodeCompletionHandlerBase(CompletionType.BASIC)
                                                    .invokeCompletion(insertContext.getProject(), editor);
                                                editor.getCaretModel().moveToOffset(editor.getCaretModel().getOffset());
                                            });
                                        }));
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
                                    suggestions.add(child.getName());
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
                                            if (typeName != null && typeName.endsWith("Repository")) {
                                                suggestions.add(typeName);
                                            }
                                        }
                                    }
                                }
                            }

                            // 添加补全建议
                            for (String suggestion : suggestions) {
                                String[] parts = suggestion.split("\\.");
                                String lastPart = parts[parts.length - 1];
                                boolean needsDot = !currentText.endsWith(".");
                                
                                result.addElement(LookupElementBuilder.create(lastPart)
                                        .withPresentableText(lastPart)
                                        .withTypeText("Package")
                                        .withInsertHandler((insertContext, item) -> {
                                            if (needsDot) {
                                                Editor editor = insertContext.getEditor();
                                                Document document = editor.getDocument();
                                                document.insertString(insertContext.getTailOffset(), ".");
                                                editor.getCaretModel().moveToOffset(insertContext.getTailOffset());
                                                new CodeCompletionHandlerBase(CompletionType.BASIC)
                                                    .invokeCompletion(project, editor);
                                            }
                                        }));
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
}