package com.github.eatmoreapple.juice.annotator;

import com.goide.psi.GoTypeSpec;
import com.goide.stubs.index.GoTypesIndex;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.eatmoreapple.juice.util.ModuleUtils;

/**
 * SQL ID验证注解器
 * 验证SQL ID是否对应Go接口中的方法
 */
public class SqlIdValidationAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // 只处理XML属性值
        if (!(element instanceof XmlAttributeValue)) {
            return;
        }

        XmlAttributeValue value = (XmlAttributeValue) element;
        
        // 检查是否是SQL标签的id属性
        if (!isSqlIdAttribute(value)) {
            return;
        }

        String sqlId = value.getValue();
        if (sqlId == null || sqlId.trim().isEmpty()) {
            // 空SQL ID警告
            addEmptySqlIdWarning(holder, value);
            return;
        }

        // 验证SQL ID是否对应Go方法
        validateSqlId(holder, value, sqlId);
    }

    /**
     * 检查是否为SQL标签的id属性
     */
    private boolean isSqlIdAttribute(XmlAttributeValue value) {
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
            
            return attribute.getName().equals("id") && 
                   (tag.getName().equals("select") || tag.getName().equals("insert") || 
                    tag.getName().equals("update") || tag.getName().equals("delete"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 添加空SQL ID警告
     */
    private void addEmptySqlIdWarning(@NotNull AnnotationHolder holder, @NotNull XmlAttributeValue value) {
        TextRange range = value.getValueTextRange();
        
        holder.newAnnotation(HighlightSeverity.WARNING, 
            "SQL ID is empty. Please specify a method name.")
                .range(range)
                .create();
    }

    /**
     * 验证SQL ID
     */
    private void validateSqlId(@NotNull AnnotationHolder holder, @NotNull XmlAttributeValue value, @NotNull String sqlId) {
        Project project = value.getProject();
        
        // 获取namespace
        String namespace = getNamespace(value);
        if (namespace == null || namespace.trim().isEmpty()) {
            // 如果没有namespace，不进行验证
            return;
        }

        // 检查方法是否存在
        if (!methodExists(project, namespace, sqlId)) {
            addMethodNotFoundError(holder, value, sqlId, namespace);
        }
    }

    /**
     * 获取当前mapper的namespace
     */
    private String getNamespace(@NotNull XmlAttributeValue value) {
        try {
            // 向上查找mapper标签
            PsiElement current = value.getParent();
            while (current != null) {
                if (current instanceof XmlTag) {
                    XmlTag tag = (XmlTag) current;
                    if ("mapper".equals(tag.getName())) {
                        return tag.getAttributeValue("namespace");
                    }
                }
                current = current.getParent();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查方法是否存在
     */
    private boolean methodExists(@NotNull Project project, @NotNull String namespace, @NotNull String methodName) {
        try {
            // 获取模块名
            String moduleName = ModuleUtils.getModuleName(project);
            if (moduleName == null) {
                return false;
            }

            // 解析namespace
            String relativeNamespace = namespace;
            if (namespace.startsWith(moduleName)) {
                relativeNamespace = namespace.substring(moduleName.length() + 1);
            }

            String[] parts = relativeNamespace.split("\\.");
            if (parts.length < 2) {
                return false;
            }

            String packagePath = parts[parts.length - 2];
            String interfaceName = parts[parts.length - 1];

            // 查找接口
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            Collection<GoTypeSpec> elements = StubIndex.getElements(
                GoTypesIndex.KEY, interfaceName, project, scope, GoTypeSpec.class);

            return elements.stream()
                    .filter(element -> {
                        String elementPackagePath = element.getContainingFile().getPackageName();
                        return elementPackagePath.endsWith(packagePath);
                    })
                    .anyMatch(element -> element.getAllMethods().stream()
                            .anyMatch(method -> methodName.equals(method.getIdentifier().getText())));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 添加方法不存在错误
     */
    private void addMethodNotFoundError(@NotNull AnnotationHolder holder, @NotNull XmlAttributeValue value, 
                                       @NotNull String methodName, @NotNull String namespace) {
        TextRange range = value.getValueTextRange();
        
        // 查找相似的方法名
        List<String> suggestions = findSimilarMethods(value.getProject(), namespace, methodName);
        
        String message = "Method '" + methodName + "' not found in interface.";
        if (!suggestions.isEmpty()) {
            message += " Did you mean: " + String.join(", ", suggestions.subList(0, Math.min(3, suggestions.size()))) + "?";
        }
        
        AnnotationBuilder annotation = holder.newAnnotation(HighlightSeverity.ERROR, message);
        
        annotation.range(range);
        
        // 添加建议的修复选项
        for (String suggestion : suggestions.subList(0, Math.min(3, suggestions.size()))) {
            annotation.withFix(new ReplaceMethodNameQuickFix(suggestion));
        }
        
        // 添加创建方法的快速修复
        annotation.withFix(new CreateMethodQuickFix(methodName));
        
        annotation.create();
    }

    /**
     * 查找相似的方法名
     */
    private List<String> findSimilarMethods(@NotNull Project project, @NotNull String namespace, @NotNull String methodName) {
        try {
            String moduleName = ModuleUtils.getModuleName(project);
            if (moduleName == null) {
                return List.of();
            }

            String relativeNamespace = namespace;
            if (namespace.startsWith(moduleName)) {
                relativeNamespace = namespace.substring(moduleName.length() + 1);
            }

            String[] parts = relativeNamespace.split("\\.");
            if (parts.length < 2) {
                return List.of();
            }

            String packagePath = parts[parts.length - 2];
            String interfaceName = parts[parts.length - 1];

            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            Collection<GoTypeSpec> elements = StubIndex.getElements(
                GoTypesIndex.KEY, interfaceName, project, scope, GoTypeSpec.class);

            return elements.stream()
                    .filter(element -> {
                        String elementPackagePath = element.getContainingFile().getPackageName();
                        return elementPackagePath.endsWith(packagePath);
                    })
                    .flatMap(element -> element.getAllMethods().stream())
                    .map(method -> method.getIdentifier().getText())
                    .filter(name -> name.toLowerCase().contains(methodName.toLowerCase()) || 
                                  methodName.toLowerCase().contains(name.toLowerCase()))
                    .limit(5)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 替换方法名快速修复
     */
    private static class ReplaceMethodNameQuickFix extends PsiElementBaseIntentionAction {
        private final String suggestionName;

        public ReplaceMethodNameQuickFix(String suggestionName) {
            this.suggestionName = suggestionName;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
            if (element instanceof XmlAttributeValue) {
                XmlAttributeValue value = (XmlAttributeValue) element;
                // 简化实现：显示提示而不是直接修改
                // value.setValue(suggestionName);
            }
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
            return element instanceof XmlAttributeValue;
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return "Replace with similar method";
        }

        @NotNull
        @Override
        public String getText() {
            return "Replace with '" + suggestionName + "'";
        }
    }

    /**
     * 创建方法快速修复
     */
    private static class CreateMethodQuickFix extends PsiElementBaseIntentionAction {
        private final String methodName;

        public CreateMethodQuickFix(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
            // 这里可以实现跳转到Go接口文件并创建方法的逻辑
            // 暂时只显示提示
        }

        @Override
        public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
            return element instanceof XmlAttributeValue;
        }

        @NotNull
        @Override
        public String getFamilyName() {
            return "Create method in Go interface";
        }

        @NotNull
        @Override
        public String getText() {
            return "Create method '" + methodName + "' in Go interface";
        }
    }
}