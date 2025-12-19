package com.github.eatmoreapple.juice.reference;

import com.goide.psi.GoFile;
import com.goide.psi.GoTypeSpec;
import com.goide.stubs.index.GoTypesIndex;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
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
import java.util.Collection;
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

                        // 检查是否为 main 包
                        boolean isMain = value.startsWith("main.");
                        String path;
                        int startOffsetInValue;

                        if (isMain) {
                            path = value.substring(5); // "main." is 5 chars
                            startOffsetInValue = 5;
                        } else {
                            // 获取模块名
                            String moduleName = ModuleUtils.getModuleName(project);
                            if (moduleName == null || !value.startsWith(moduleName)) {
                                return PsiReference.EMPTY_ARRAY;
                            }
                            path = value.substring(moduleName.length());
                            if (path.startsWith(".")) {
                                path = path.substring(1);
                                startOffsetInValue = moduleName.length() + 1;
                            } else {
                                startOffsetInValue = moduleName.length();
                            }
                        }

                        // 分割路径
                        String[] parts = path.split("\\.");
                        List<PsiReference> references = new ArrayList<>();
                        
                        // 计算每个部分的文本范围并创建引用
                        int baseOffset = xmlAttributeValue.getValueTextRange().getStartOffset() - xmlAttributeValue.getTextRange().getStartOffset();
                        
                        if (isMain) {
                            // 为接口部分（第一部分）创建引用
                            if (parts.length > 0) {
                                String interfacePart = parts[0];
                                TextRange interfaceRange = new TextRange(baseOffset + 5, baseOffset + 5 + interfacePart.length());
                                references.add(new GlobalTypeReference(element, interfaceRange, interfacePart, "main"));
                            }
                        } else {
                            int currentOffset = baseOffset + startOffsetInValue;
                            VirtualFile baseDir = project.getBaseDir();
                            VirtualFile currentDir = baseDir;
                            
                            // 为每个部分创建引用
                            for (int i = 0; i < parts.length; i++) {
                                String part = parts[i];
                                if (part.isEmpty()) continue;
                                
                                TextRange range = new TextRange(currentOffset, currentOffset + part.length());
                                
                                // 检查是否是最后一个部分
                                if (i == parts.length - 1) {
                                    // 最后一个部分可能是类型名称，创建类型引用
                                    references.add(new TypeReference(element, range, currentDir, part));
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
     * 类型引用
     */
    private static class TypeReference extends PsiReferenceBase<PsiElement> {
        private final VirtualFile currentDir;
        private final String targetName;

        public TypeReference(@NotNull PsiElement element, @NotNull TextRange range,
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
                        // 查找目标类型（不再限制仅查找接口）
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

    /**
     * 全局类型引用（用于 main 包）
     */
    private static class GlobalTypeReference extends PsiReferenceBase<PsiElement> {
        private final String targetName;
        private final String packageName;

        public GlobalTypeReference(@NotNull PsiElement element, @NotNull TextRange range,
                                 @NotNull String targetName, @NotNull String packageName) {
            super(element, range);
            this.targetName = targetName;
            this.packageName = packageName;
        }

        @Override
        public @Nullable PsiElement resolve() {
            Project project = getElement().getProject();
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            
            Collection<GoTypeSpec> elements = StubIndex.getElements(
                GoTypesIndex.KEY, targetName, project, scope, GoTypeSpec.class);
            
            return elements.stream()
                .filter(typeSpec -> {
                    PsiFile file = typeSpec.getContainingFile();
                    if (file instanceof GoFile) {
                        return packageName.equals(((GoFile) file).getPackageName());
                    }
                    return false;
                })
                .findFirst()
                .orElse(null);
        }
    }
}
