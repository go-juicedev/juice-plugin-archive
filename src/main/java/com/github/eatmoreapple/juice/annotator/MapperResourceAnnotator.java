package com.github.eatmoreapple.juice.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class MapperResourceAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        // 只处理XML属性值
        if (!(element instanceof XmlAttributeValue)) {
            return;
        }

        XmlAttributeValue value = (XmlAttributeValue) element;
        
        // 检查是否是mapper标签的resource属性
        if (!isMapperResourceAttribute(value)) {
            return;
        }

        // 获取文件引用
        PsiReference[] references = value.getReferences();
        if (references.length == 0) {
            return;
        }

        // 检查最后一个引用（完整路径）是否能解析到文件
        PsiReference lastRef = references[references.length - 1];
        if (lastRef instanceof FileReference && lastRef.resolve() == null) {
            // 如果文件不存在，添加错误标记
            TextRange range = new TextRange(
                value.getValueTextRange().getStartOffset(),
                value.getValueTextRange().getEndOffset()
            );
            holder.newAnnotation(HighlightSeverity.ERROR, "Cannot resolve mapper file")
                    .range(range)
                    .create();
        }
    }

    private boolean isMapperResourceAttribute(XmlAttributeValue value) {
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
            
            return tag.getName().equals("mapper") && attribute.getName().equals("resource");
        } catch (Exception e) {
            return false;
        }
    }
}
