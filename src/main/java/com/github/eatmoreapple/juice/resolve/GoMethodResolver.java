package com.github.eatmoreapple.juice.resolve;

import com.github.eatmoreapple.juice.util.ModuleUtils;
import com.goide.psi.GoFile;
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
            boolean isMainNamespace = namespace.startsWith("main.");
            String interfacePath;
            String moduleName = "";
            if (isMainNamespace) {
                interfacePath = namespace.replace(".", "/");
            } else {
                moduleName = ModuleUtils.getModuleName(project);
                if (moduleName == null || !namespace.startsWith(moduleName)) {
                    return null;
                }
                interfacePath = namespace.substring(moduleName.length()).replace(".", "/");
                if (interfacePath.startsWith("/")) {
                    interfacePath = interfacePath.substring(1);
                }
            }

            String[] pathParts = interfacePath.split("/");
            String interfaceName = pathParts[pathParts.length - 1];
            String dirPath = interfacePath.contains("/") ? interfacePath.substring(0, interfacePath.lastIndexOf("/")) : "";

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
                            if (!interfaceName.equals(methodInterfaceName)) {
                                return false;
                            }
                            if (isMainNamespace) {
                                // 对于 main 包，直接校验包名
                                if (parent.getContainingFile() instanceof GoFile goFile) {
                                    return "main".equals(goFile.getPackageName());
                                }
                                return false;
                            } else {
                                // 对于非 main 包，校验目录路径
                                String methodDirPath = parent.getContainingFile().getVirtualFile().getParent().getPath();
                                return methodDirPath.endsWith(dirPath);
                            }
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
