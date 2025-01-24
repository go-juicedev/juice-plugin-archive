package com.github.eatmoreapple.juice.marker;

import com.goide.psi.GoMethodSpec;
import com.goide.psi.GoSpecType;
import com.goide.psi.impl.GoSpecTypeImpl;
import com.goide.stubs.index.GoMethodSpecFingerprintIndex;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author pjh
 * @date 2024/12/27
 */
public class SqlIdLineMarkerProvider extends RelatedItemLineMarkerProvider {

    private static final Logger log = LoggerFactory.getLogger(SqlIdLineMarkerProvider.class);

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (element instanceof XmlTag xmlElement) {
            PsiElement parent = element.getParent();
            if (parent instanceof XmlTag parentXml && parentXml.getName().equals("mapper")) {
                String sqlId = xmlElement.getAttributeValue("id"); // 获取 SQL 方法 ID
                if (sqlId != null) {
                    // 查找对应的 Go 方法
                    PsiElement target = findGoMethodById(sqlId, xmlElement.getProject(), ((XmlTag) parent).getAttributeValue("namespace"));
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

    private PsiElement findGoMethodById(String id, Project project, String namespace) {
        try {
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            HashSet<String> allKeys = (HashSet<String>) StubIndex.getInstance().getAllKeys(GoMethodSpecFingerprintIndex.KEY, project);
            for (String key : allKeys) {
                if (key.split("/")[0].equals(id)) {
                    Collection<GoMethodSpec> elements = StubIndex.getElements(GoMethodSpecFingerprintIndex.KEY, key, project, scope, GoMethodSpec.class);
                    if (!elements.isEmpty()) {
                        return elements.stream().toList().stream()
                                .filter(e -> {
                                    if (e.getParent().getContext() instanceof GoSpecType parent) {
                                        String interfaceName = parent.getIdentifier().getText();
                                        return namespace.endsWith(interfaceName);
                                    }
                                    return false;
                                })
                                .findFirst()
                                .orElse(null);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to find Go method by id: {}", id, e);
        }
        return null;
    }

}
