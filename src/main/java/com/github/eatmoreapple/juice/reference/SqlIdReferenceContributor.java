package com.github.eatmoreapple.juice.reference;

import com.github.eatmoreapple.juice.marker.SqlIdLineMarkerProvider;
import com.goide.psi.GoMethodSpec;
import com.goide.psi.GoSpecType;
import com.goide.stubs.index.GoMethodSpecFingerprintIndex;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.eatmoreapple.juice.util.ModuleUtils;

import java.util.Collection;

public class SqlIdReferenceContributor extends PsiReferenceContributor {
    private static final Logger log = LoggerFactory.getLogger(SqlIdLineMarkerProvider.class);

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                // 匹配 select、insert、update、delete 标签中的 id 属性值
                XmlPatterns.xmlAttributeValue()
                        .withParent(XmlPatterns.xmlAttribute().withName("id")
                                .withParent(XmlPatterns.xmlTag().withName(
                                        PlatformPatterns.string().oneOf("select", "insert", "update", "delete")
                                ))),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                         @NotNull ProcessingContext context) {
                        XmlAttributeValue value = (XmlAttributeValue) element;
                        String id = value.getValue();

                        // 获取 mapper 标签
                        PsiElement current = value.getParent().getParent(); // 从属性值到标签
                        if (current instanceof XmlTag tag) {
                            XmlTag mapperTag = tag.getParentTag();
                            if (mapperTag != null && mapperTag.getName().equals("mapper")) {
                                String namespace = mapperTag.getAttributeValue("namespace");
                                if (namespace != null && !namespace.isEmpty()) {
                                    return new PsiReference[]{new SqlIdReference(value, namespace)};
                                }
                            }
                        }
                        return PsiReference.EMPTY_ARRAY;
                    }
                }
        );
    }

    private static class SqlIdReference extends PsiReferenceBase<XmlAttributeValue> {
        private final String namespace;

        protected SqlIdReference(@NotNull XmlAttributeValue element, String namespace) {
            super(element);
            this.namespace = namespace;
        }

        @Override
        public PsiElement resolve() {
            try {
                String id = getElement().getValue();
                Project project = getElement().getProject();

                // 解析命名空间路径
                String moduleName = ModuleUtils.getModuleName(project);
                String interfacePath = namespace.substring(moduleName.length()).replace(".", "/");
                String[] pathParts = interfacePath.split("/");
                String interfaceName = pathParts[pathParts.length - 1];
                String dirPath = interfacePath.substring(0, interfacePath.lastIndexOf("/"));
                log.info("Interface path: {}, Interface name: {}", interfacePath, interfaceName);

                // 搜索匹配的方法
                GlobalSearchScope scope = GlobalSearchScope.allScope(project);
                Collection<GoMethodSpec> matchedMethods = StubIndex.getElements(
                        GoMethodSpecFingerprintIndex.KEY,
                        StubIndex.getInstance().getAllKeys(GoMethodSpecFingerprintIndex.KEY, project).stream()
                                .filter(key -> key.split("/")[0].equals(id))
                                .findFirst()
                                .orElse(""),
                        project,
                        scope,
                        GoMethodSpec.class
                );

                // 过滤并返回匹配的方法
                return matchedMethods.stream()
                        .filter(method -> {
                            if (method.getParent().getContext() instanceof GoSpecType parent) {
                                String methodInterfaceName = parent.getIdentifier().getText();
                                String methodDirPath = parent.getContainingFile().getVirtualFile().getParent().getPath();
                                return methodDirPath.endsWith(dirPath) && interfaceName.equals(methodInterfaceName);
                            }
                            return false;
                        })
                        .findFirst()
                        .orElse(null);
            } catch (Exception e) {
                log.warn("Failed to resolve reference for id: {}", getElement().getValue(), e);
                return null;
            }
        }
    }
}