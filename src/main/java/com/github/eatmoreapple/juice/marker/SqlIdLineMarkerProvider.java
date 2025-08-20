package com.github.eatmoreapple.juice.marker;

import com.github.eatmoreapple.juice.resolve.GoMethodResolver;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author pjh
 * @date 2024/12/27
 */
public class SqlIdLineMarkerProvider extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (element instanceof XmlTag xmlElement) {
            PsiElement parent = element.getParent();
            if (parent instanceof XmlTag parentXml && parentXml.getName().equals("mapper")) {
                String sqlId = xmlElement.getAttributeValue("id"); // 获取 SQL 方法 ID
                if (sqlId != null) {
                    // 查找对应的 Go 方法
                    PsiElement target = GoMethodResolver.resolveBySqlId(xmlElement.getProject(), sqlId, parentXml.getAttributeValue("namespace"));
                    if (target != null) {
                        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                                .create(AllIcons.Gutter.ImplementingMethod)
                                .setTarget(target)
                                .setTooltipText("Navigate to Go method");
                        RelatedItemLineMarkerInfo<PsiElement> lineMarkerInfo = builder.createLineMarkerInfo(xmlElement);
                        result.add(lineMarkerInfo);
                    }
                }
            }
        }
    }
}
