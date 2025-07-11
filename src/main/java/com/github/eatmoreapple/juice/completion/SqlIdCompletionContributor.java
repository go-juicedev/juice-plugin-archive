package com.github.eatmoreapple.juice.completion;

import com.goide.psi.GoTypeSpec;
import com.goide.psi.GoNamedSignatureOwner;
import com.goide.stubs.index.GoTypesIndex;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.JBColor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.github.eatmoreapple.juice.util.ModuleUtils;

/**
 * @author pjh
 * @date 2025/1/24
 */
public class SqlIdCompletionContributor extends CompletionContributor {
    private static final Logger log = LoggerFactory.getLogger(SqlIdCompletionContributor.class);

    public SqlIdCompletionContributor() {
        // 为 id 属性添加自动完成
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().inside(
                        XmlPatterns.xmlAttribute().withName("id").withParent(
                                XmlPatterns.xmlTag().withName(
                                        PlatformPatterns.string().oneOf("select", "insert", "update", "delete")
                                )
                        )
                ),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        try {
                            PsiElement position = parameters.getPosition();

                            // 向上查找到 XmlAttribute
                            PsiElement current = position;
                            while (current != null && !(current instanceof XmlAttribute)) {
                                current = current.getParent();
                            }

                            if (!(current instanceof XmlAttribute)) {
                                return;
                            }

                            XmlAttribute attribute = (XmlAttribute) current;
                            XmlTag tag = attribute.getParent();

                            if (tag == null) {
                                return;
                            }

                            // 获取 mapper 标签
                            XmlTag mapperTag = tag.getParentTag();
                            if (mapperTag == null || !mapperTag.getName().equals("mapper")) {
                                return;
                            }

                            // 获取 namespace
                            String namespace = mapperTag.getAttributeValue("namespace");
                            if (namespace == null || namespace.isEmpty()) {
                                return;
                            }

                            // 获取当前项目的 module name
                            String moduleName = ModuleUtils.getModuleName(position.getProject());
                            if (moduleName == null) {
                                return;
                            }

                            // 去掉 namespace 中的 module name 前缀
                            String relativeNamespace = namespace;
                            if (namespace.startsWith(moduleName)) {
                                relativeNamespace = namespace.substring(moduleName.length() + 1); // 去掉前缀和后面的 "."
                            }

                            // 解析 relativeNamespace 获取包路径和接口名
                            String[] parts = relativeNamespace.split("\\.");
                            if (parts.length < 2) {
                                return; // 格式不正确
                            }
                            String packagePath = parts[parts.length - 2]; // 包路径
                            String interfaceName = parts[parts.length - 1]; // 接口名

                            // 查找匹配的 GoTypeSpec
                            GlobalSearchScope globalSearchScope = GlobalSearchScope.allScope(position.getProject());
                            Collection<GoTypeSpec> elements = StubIndex.getElements(
                                    GoTypesIndex.KEY, interfaceName, position.getProject(), globalSearchScope, GoTypeSpec.class);

                            // 过滤出包路径和接口名匹配的 GoTypeSpec，并收集方法信息
                            List<MethodInfo> methods = elements.stream()
                                    .filter(element -> {
                                        // 检查包路径是否匹配
                                        String elementPackagePath = element.getContainingFile().getPackageName();
                                        return elementPackagePath.endsWith(packagePath); // 检查包路径是否以目标包路径结尾
                                    })
                                    .flatMap(element -> element.getAllMethods().stream()
                                            .map(method -> new MethodInfo(
                                                    method.getIdentifier().getText(),
                                                    buildMethodSignature(method),
                                                    getMethodDescription(method, tag.getName())
                                            )))
                                    .collect(Collectors.toList());

                            // 添加方法到自动完成列表，带有详细信息
                            methods.forEach(methodInfo -> {
                                LookupElementBuilder element = LookupElementBuilder.create(methodInfo.name)
                                        .withLookupString(methodInfo.name.toLowerCase())
                                        .withTypeText(methodInfo.signature)
                                        .withTailText(methodInfo.description)
                                        .withIcon(getMethodIcon(tag.getName()))
                                        .withItemTextForeground(getMethodColor(tag.getName()))
                                        .withBoldness(true);
                                
                                result.addElement(element);
                            });
                        } catch (Exception e) {
                            log.warn("Error in completion contributor", e);
                        }
                    }
                }
        );
    }

    /**
     * 方法信息类
     */
    private static class MethodInfo {
        final String name;
        final String signature;
        final String description;

        public MethodInfo(String name, String signature, String description) {
            this.name = name;
            this.signature = signature;
            this.description = description;
        }
    }

    /**
     * 构建方法签名字符串
     */
    private String buildMethodSignature(@NotNull GoNamedSignatureOwner method) {
        try {
            // 简化实现，只返回方法名
            return method.getName() != null ? method.getName() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 格式化参数
     */
    private String formatParameter(Object param) {
        return param.toString();
    }

    /**
     * 获取方法描述
     */
    private String getMethodDescription(@NotNull GoNamedSignatureOwner method, @NotNull String sqlType) {
        return " (" + sqlType.toUpperCase() + " operation)";
    }

    /**
     * 根据SQL类型获取图标
     */
    private javax.swing.Icon getMethodIcon(@NotNull String sqlType) {
        switch (sqlType.toLowerCase()) {
            case "select":
                return AllIcons.Actions.Find;
            case "insert":
                return AllIcons.Actions.AddMulticaret;
            case "update":
                return AllIcons.Actions.Edit;
            case "delete":
                return AllIcons.Actions.Cancel;
            default:
                return AllIcons.Nodes.Method;
        }
    }

    /**
     * 根据SQL类型获取方法名颜色
     */
    private java.awt.Color getMethodColor(@NotNull String sqlType) {
        switch (sqlType.toLowerCase()) {
            case "select":
                return JBColor.BLUE;        // 蓝色 - 查询操作
            case "insert":
                return JBColor.GREEN;       // 绿色 - 插入操作
            case "update":
                return JBColor.ORANGE;      // 橙色 - 更新操作
            case "delete":
                return JBColor.RED;         // 红色 - 删除操作
            default:
                return JBColor.BLACK;       // 默认黑色
        }
    }
}