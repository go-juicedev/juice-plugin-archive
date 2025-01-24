package com.github.eatmoreapple.juice.reference;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqlIncludeReferenceContributor extends PsiReferenceContributor {
    private static final Logger log = LoggerFactory.getLogger(SqlIncludeReferenceContributor.class);

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
                // 匹配 include 标签中的 refid 属性值
                XmlPatterns.xmlAttributeValue()
                        .withParent(XmlPatterns.xmlAttribute().withName("refid")
                                .withParent(XmlPatterns.xmlTag().withName("include"))),
                new PsiReferenceProvider() {
                    @Override
                    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                                         @NotNull ProcessingContext context) {
                        XmlAttributeValue value = (XmlAttributeValue) element;
                        return new PsiReference[]{new SqlIncludeReference(value)};
                    }
                }
        );
    }

    private static class SqlIncludeReference extends PsiReferenceBase<XmlAttributeValue> {
        protected SqlIncludeReference(@NotNull XmlAttributeValue element) {
            super(element);
        }

        @Override
        public PsiElement resolve() {
            try {
                String refid = getElement().getValue();
                
                // 获取当前 mapper 文件中的所有 sql 标签
                PsiElement current = getElement();
                while (current != null && !(current instanceof XmlTag && ((XmlTag) current).getName().equals("mapper"))) {
                    current = current.getParent();
                }
                
                if (current instanceof XmlTag mapperTag) {
                    // 遍历所有子标签
                    for (XmlTag subTag : mapperTag.getSubTags()) {
                        if ("sql".equals(subTag.getName())) {
                            XmlAttribute idAttr = subTag.getAttribute("id");
                            if (idAttr != null && refid.equals(idAttr.getValue())) {
                                // 找到匹配的 sql 标签
                                return idAttr.getValueElement();
                            }
                        }
                    }
                }
                
                return null;
            } catch (Exception e) {
                log.warn("Failed to resolve reference for refid: {}", getElement().getValue(), e);
                return null;
            }
        }
    }
}
