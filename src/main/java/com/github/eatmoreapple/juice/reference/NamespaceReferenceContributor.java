package com.github.eatmoreapple.juice.reference;

import com.goide.psi.GoFile;
import com.goide.psi.GoTypeSpec;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.github.eatmoreapple.juice.util.ModuleUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NamespaceReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        // 注册 mapper 标签的 namespace 属性的引用提供者
        registrar.registerReferenceProvider(
                PlatformPatterns.psiElement(XmlAttributeValue.class)
                        .inside(XmlPatterns.xmlAttribute("namespace")
                                .withParent(XmlPatterns.xmlTag().withName("mapper"))),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                         @NotNull ProcessingContext context) {
                        Project project = element.getProject();
                        XmlAttributeValue xmlAttributeValue = (XmlAttributeValue) element;
                        String value = xmlAttributeValue.getValue();
                        if (value == null) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        // 获取模块名
                        String moduleName = ModuleUtils.getModuleName(project);
                        if (moduleName == null) {
                            return PsiReference.EMPTY_ARRAY;
                        }

                        // 解析路径部分
                        if (value.length() < moduleName.length()) {
                            return PsiReference.EMPTY_ARRAY;
                        }
                        String path = value.substring(moduleName.length());
                        if (path.startsWith(".")) {
                            path = path.substring(1);
                        }

                        // 分割路径
                        String[] parts = path.split("\\.");
                        List<PsiReference> references = new ArrayList<>();
                        
                        // 计算每个部分的文本范围并创建引用
                        int startOffset = xmlAttributeValue.getValueTextRange().getStartOffset() - xmlAttributeValue.getTextRange().getStartOffset();
                        int currentOffset = startOffset + moduleName.length() + 1; // +1 for the dot after module name
                        
                        VirtualFile baseDir = project.getBaseDir();
                        VirtualFile currentDir = baseDir;
                        
                        // 为每个部分创建引用
                        for (int i = 0; i < parts.length; i++) {
                            String part = parts[i];
                            if (part.isEmpty()) continue;
                            
                            TextRange range = new TextRange(currentOffset, currentOffset + part.length());
                            
                            // 检查是否是最后一个部分（可能是接口）
                            if (i == parts.length - 1 && part.endsWith("Repository")) {
                                // 为接口创建引用
                                references.add(new InterfaceReference(element, range, currentDir, part));
                            } else {
                                // 为目录创建引用
                                references.add(new DirectoryReference(element, range, currentDir, part));
                            }
                            
                            // 更新当前目录和偏移量
                            VirtualFile nextDir = currentDir.findChild(part);
                            if (nextDir != null && nextDir.isDirectory()) {
                                currentDir = nextDir;
                            }
                            currentOffset += part.length() + 1; // +1 for the dot
                        }
                        
                        return references.toArray(new PsiReference[0]);
                    }
                });
    }

    /**
     * 目录引用
     */
    private static class DirectoryReference extends PsiReferenceBase<PsiElement> {
        private final VirtualFile currentDir;
        private final String targetName;

        public DirectoryReference(@NotNull PsiElement element, @NotNull TextRange range,
                                @NotNull VirtualFile currentDir, @NotNull String targetName) {
            super(element, range);
            this.currentDir = currentDir;
            this.targetName = targetName;
        }

        @Override
        public @Nullable PsiElement resolve() {
            VirtualFile targetDir = currentDir.findChild(targetName);
            if (targetDir != null && targetDir.isDirectory()) {
                return PsiManager.getInstance(getElement().getProject()).findDirectory(targetDir);
            }
            return null;
        }
    }

    /**
     * 接口引用
     */
    private static class InterfaceReference extends PsiReferenceBase<PsiElement> {
        private final VirtualFile currentDir;
        private final String targetName;

        public InterfaceReference(@NotNull PsiElement element, @NotNull TextRange range,
                                @NotNull VirtualFile currentDir, @NotNull String targetName) {
            super(element, range);
            this.currentDir = currentDir;
            this.targetName = targetName;
        }

        @Override
        public @Nullable PsiElement resolve() {
            Project project = getElement().getProject();
            PsiManager psiManager = PsiManager.getInstance(project);
            
            // 遍历当前目录下的所有 Go 文件
            for (VirtualFile child : currentDir.getChildren()) {
                if (!child.isDirectory() && "go".equals(child.getExtension())) {
                    PsiFile psiFile = psiManager.findFile(child);
                    if (psiFile instanceof GoFile) {
                        GoFile goFile = (GoFile) psiFile;
                        // 查找目标接口
                        for (GoTypeSpec typeSpec : goFile.getTypes()) {
                            if (targetName.equals(typeSpec.getName())) {
                                return typeSpec;
                            }
                        }
                    }
                }
            }
            return null;
        }
    }
}
