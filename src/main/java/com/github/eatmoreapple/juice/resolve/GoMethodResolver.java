package com.github.eatmoreapple.juice.resolve;

import com.github.eatmoreapple.juice.util.ModuleUtils;
import com.goide.psi.GoMethodSpec;
import com.goide.psi.GoSpecType;
import com.goide.stubs.index.GoMethodSpecFingerprintIndex;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author cunshu
 * @date 2025/8/20
 */
public class GoMethodResolver {

    private GoMethodResolver() {
    }

    private static final Logger log = LoggerFactory.getLogger(GoMethodResolver.class);


    public static PsiElement resolveBySqlId(Project project, String id, String namespace) {
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
            Collection<GoMethodSpec> matchedMethods = new ArrayList<>();
            StubIndex.getInstance().getAllKeys(GoMethodSpecFingerprintIndex.KEY, project).stream()
                    .filter(key -> key.split("/")[0].equals(id))
                    .forEach(key ->
                            matchedMethods.addAll(StubIndex.getElements(
                                    GoMethodSpecFingerprintIndex.KEY,
                                    key,
                                    project,
                                    scope,
                                    GoMethodSpec.class
                            ))
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
            log.warn("Failed to resolve Go method by id: {}", id, e);
            return null;
        }
    }

}
