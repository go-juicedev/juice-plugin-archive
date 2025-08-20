package com.github.eatmoreapple.juice.reference;

import com.github.eatmoreapple.juice.resolve.GoMethodResolver;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class SqlIdReferenceContributor extends PsiReferenceContributor {

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
            String id = getElement().getValue();
            Project project = getElement().getProject();
            return GoMethodResolver.resolveBySqlId(project, id, namespace);
        }
    }
}