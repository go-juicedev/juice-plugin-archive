package com.github.eatmoreapple.juice.reference;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

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

                // 1. 先尝试在当前文件中查找
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

                // 2. 如果当前文件没找到，或者是跨 namespace 引用（包含点号），则进行全局搜索
                if (refid.contains(".")) {
                    return resolveCrossNamespaceReference(refid);
                }

                return null;
            } catch (Exception e) {
                log.warn("Failed to resolve reference for refid: {}", getElement().getValue(), e);
                return null;
            }
        }

        private PsiElement resolveCrossNamespaceReference(String refid) {
            Project project = getElement().getProject();
            String targetNamespace;
            String targetSqlId;

            // 分离 namespace 和 sql id
            int lastDotIndex = refid.lastIndexOf('.');
            if (lastDotIndex > 0) {
                targetNamespace = refid.substring(0, lastDotIndex);
                targetSqlId = refid.substring(lastDotIndex + 1);
            } else {
                return null;
            }

            // 在整个项目中查找所有 XML 文件
            Collection<VirtualFile> xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, GlobalSearchScope.allScope(project));
            PsiManager psiManager = PsiManager.getInstance(project);

            for (VirtualFile virtualFile : xmlFiles) {
                PsiFile psiFile = psiManager.findFile(virtualFile);
                if (psiFile instanceof XmlFile xmlFile) {
                    XmlTag rootTag = xmlFile.getRootTag();
                    if (rootTag != null && "mapper".equals(rootTag.getName())) {
                        String namespace = rootTag.getAttributeValue("namespace");
                        if (targetNamespace.equals(namespace)) {
                            // 找到目标 namespace 的文件，现在查找对应的 sql 标签
                            for (XmlTag subTag : rootTag.getSubTags()) {
                                if ("sql".equals(subTag.getName())) {
                                    XmlAttribute idAttr = subTag.getAttribute("id");
                                    if (idAttr != null && targetSqlId.equals(idAttr.getValue())) {
                                        return idAttr.getValueElement();
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return null;
        }
    }
}
