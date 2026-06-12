package com.github.eatmoreapple.juice.resolve;

import com.goide.psi.GoMethodSpec;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            MapperNamespaceResolver.ResolvedNamespace resolvedNamespace = MapperNamespaceResolver.parse(project, namespace);
            if (resolvedNamespace == null) {
                return null;
            }
            GoMethodSpec method = MapperNamespaceResolver.resolveMethod(project, resolvedNamespace, id);
            return method;
        } catch (Exception e) {
            log.warn("Failed to resolve Go method by id: {}", id, e);
            return null;
        }
    }

}
