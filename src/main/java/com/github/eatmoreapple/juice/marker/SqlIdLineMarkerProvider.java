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
import com.github.eatmoreapple.juice.util.ModuleUtils;
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
            log.warn("Failed to find Go method by id: {}", id, e);
            return null;
        }
    }
}
