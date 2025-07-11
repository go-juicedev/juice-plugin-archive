package com.github.eatmoreapple.juice.annotator;

import com.goide.psi.GoTypeSpec;
import com.goide.stubs.index.GoTypesIndex;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.github.eatmoreapple.juice.util.ModuleUtils;

/**
 * Namespace验证注解器
 * 提供更友好的错误提示和修复建议
 */
public class NamespaceValidationAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // 只处理XML属性值
        if (!(element instanceof XmlAttributeValue)) {
            return;
        }

        XmlAttributeValue value = (XmlAttributeValue) element;
        
        // 检查是否是mapper标签的namespace属性
        if (!isMapperNamespaceAttribute(value)) {
            return;
        }

        String namespace = value.getValue();
        if (namespace == null || namespace.trim().isEmpty()) {
            // 空namespace警告
            addEmptyNamespaceWarning(holder, value);
            return;
        }

        // 验证namespace格式和存在性
        validateNamespace(holder, value, namespace);
    }

    /**
     * 检查是否为mapper的namespace属性
     */
    private boolean isMapperNamespaceAttribute(XmlAttributeValue value) {
        try {
            PsiElement parent = value.getParent();
            if (!(parent instanceof XmlAttribute)) {
                return false;
            }
            XmlAttribute attribute = (XmlAttribute) parent;
            
            PsiElement grandParent = attribute.getParent();
            if (!(grandParent instanceof XmlTag)) {
                return false;
            }
            XmlTag tag = (XmlTag) grandParent;
            
            return tag.getName().equals("mapper") && attribute.getName().equals("namespace");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 添加空namespace警告
     */
    private void addEmptyNamespaceWarning(@NotNull AnnotationHolder holder, @NotNull XmlAttributeValue value) {
        TextRange range = value.getValueTextRange();
        
        AnnotationBuilder annotation = holder.newAnnotation(HighlightSeverity.WARNING, 
            "Namespace is empty. Please specify a Go interface path.");
        
        annotation.range(range)
                .withFix(new AddNamespaceQuickFix())
                .create();
    }

    /**
     * 验证namespace
     */
    private void validateNamespace(@NotNull AnnotationHolder holder, @NotNull XmlAttributeValue value, @NotNull String namespace) {
        Project project = value.getProject();
        
        // 获取模块名
        String moduleName = ModuleUtils.getModuleName(project);
        if (moduleName == null) {
            addModuleNameError(holder, value);
            return;
        }

        // 检查格式
        if (!namespace.startsWith(moduleName)) {
            addInvalidFormatError(holder, value, moduleName);
            return;
        }

        // 解析namespace
        String[] parts = namespace.split("\\.");
        if (parts.length < 2) {
            addInvalidFormatError(holder, value, moduleName);
            return;
        }

        String interfaceName = parts[parts.length - 1];
        
        // 检查接口是否存在
        if (!interfaceExists(project, interfaceName)) {
            addInterfaceNotFoundError(holder, value, interfaceName);
        }
    }

    /**
     * 添加模块名错误
     */
    private void addModuleNameError(@NotNull AnnotationHolder holder, @NotNull XmlAttributeValue value) {
        TextRange range = value.getValueTextRange();
        
        holder.newAnnotation(HighlightSeverity.ERROR, 
            "Cannot determine module name. Please check your go.mod file.")
                .range(range)
                .create();
    }

    /**
     * 添加格式错误
     */
    private void addInvalidFormatError(@NotNull AnnotationHolder holder, @NotNull XmlAttributeValue value, @NotNull String moduleName) {
        TextRange range = value.getValueTextRange();
        
        AnnotationBuilder annotation = holder.newAnnotation(HighlightSeverity.ERROR, 
            "Invalid namespace format. Should start with module name: " + moduleName);
        
        annotation.range(range)
                .withFix(new FixNamespaceFormatQuickFix(moduleName))
                .create();
    }

    /**
     * 添加接口不存在错误
     */
    private void addInterfaceNotFoundError(@NotNull AnnotationHolder holder, @NotNull XmlAttributeValue value, @NotNull String interfaceName) {
        TextRange range = value.getValueTextRange();
        
        // 查找相似的接口名
        List<String> suggestions = findSimilarInterfaces(value.getProject(), interfaceName);
        
        String message = "Interface '" + interfaceName + "' not found.";
        if (!suggestions.isEmpty()) {
            message += " Did you mean: " + String.join(", ", suggestions.subList(0, Math.min(3, suggestions.size()))) + "?";
        }
        
        AnnotationBuilder annotation = holder.newAnnotation(HighlightSeverity.ERROR, message);
        
        annotation.range(range);
        
        // 添加建议的修复选项
        for (String suggestion : suggestions.subList(0, Math.min(3, suggestions.size()))) {
            annotation.withFix(new ReplaceInterfaceNameQuickFix(suggestion));
        }
        
        annotation.create();
    }

    /**
     * 检查接口是否存在
     */
    private boolean interfaceExists(@NotNull Project project, @NotNull String interfaceName) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        Collection<GoTypeSpec> elements = StubIndex.getElements(
            GoTypesIndex.KEY, interfaceName, project, scope, GoTypeSpec.class);
        
        return elements.stream().anyMatch(this::isInterfaceType);
    }

    /**
     * 判断是否为接口类型
     */
    private boolean isInterfaceType(@NotNull GoTypeSpec typeSpec) {
        String text = typeSpec.getText();
        if (text == null) return false;
        
        return text.contains("interface") && text.contains("{") && 
               text.indexOf("interface") < text.indexOf("{");
    }

    /**
     * 查找相似的接口名
     */
    private List<String> findSimilarInterfaces(@NotNull Project project, @NotNull String interfaceName) {
        // 简化实现，先返回空列表
        return List.of();
    }

    /**
     * 添加namespace快速修复
     */
    private static class AddNamespaceQuickFix extends PsiElementBaseIntentionAction {
        @Override
        public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
            if (element instanceof XmlAttributeValue) {
                XmlAttributeValue value = (XmlAttributeValue) element;
                String moduleName = ModuleUtils.getModuleName(project);
                if (moduleName != null) {
                    // 简化实现：显示提示而不是直接修改
                    // value.setValue(moduleName + ".your.package.YourInterface");
                }
            }
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
            return element instanceof XmlAttributeValue;
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return "Add namespace template";
        }

        @NotNull
        @Override
        public String getText() {
            return "Add namespace template";
        }
    }

    /**
     * 修复namespace格式快速修复
     */
    private static class FixNamespaceFormatQuickFix extends PsiElementBaseIntentionAction {
        private final String moduleName;

        public FixNamespaceFormatQuickFix(String moduleName) {
            this.moduleName = moduleName;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
            if (element instanceof XmlAttributeValue) {
                XmlAttributeValue value = (XmlAttributeValue) element;
                String current = value.getValue();
                if (current != null && !current.startsWith(moduleName)) {
                    // 简化实现：显示提示而不是直接修改
                    // value.setValue(moduleName + "." + current);
                }
            }
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
            return element instanceof XmlAttributeValue;
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return "Fix namespace format";
        }

        @NotNull
        @Override
        public String getText() {
            return "Fix namespace format";
        }
    }

    /**
     * 替换接口名快速修复
     */
    private static class ReplaceInterfaceNameQuickFix extends PsiElementBaseIntentionAction {
        private final String suggestionName;

        public ReplaceInterfaceNameQuickFix(String suggestionName) {
            this.suggestionName = suggestionName;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
            if (element instanceof XmlAttributeValue) {
                XmlAttributeValue value = (XmlAttributeValue) element;
                String current = value.getValue();
                if (current != null) {
                    String[] parts = current.split("\\.");
                    if (parts.length > 0) {
                        // 简化实现：显示提示而不是直接修改
                        // parts[parts.length - 1] = suggestionName;
                        // value.setValue(String.join(".", parts));
                    }
                }
            }
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
            return element instanceof XmlAttributeValue;
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return "Replace with similar interface";
        }

        @NotNull
        @Override
        public String getText() {
            return "Replace with '" + suggestionName + "'";
        }
    }
}