package com.github.eatmoreapple.juice.resolve;

import com.github.eatmoreapple.juice.util.ModuleUtils;
import com.goide.psi.GoFile;
import com.goide.psi.GoMethodSpec;
import com.goide.psi.GoTypeSpec;
import com.goide.stubs.index.GoMethodSpecFingerprintIndex;
import com.goide.stubs.index.GoTypesIndex;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Shared resolver for mapper namespace -> Go type/method lookup.
 */
public final class MapperNamespaceResolver {
    private MapperNamespaceResolver() {
    }

    public static @Nullable ResolvedNamespace parse(@NotNull Project project, @NotNull String namespace) {
        if (namespace.isBlank()) {
            return null;
        }

        if (namespace.startsWith("main.")) {
            String interfaceName = namespace.substring("main.".length()).trim();
            if (interfaceName.isEmpty()) {
                return null;
            }
            return new ResolvedNamespace(namespace, interfaceName, "", true);
        }

        String moduleName = ModuleUtils.getModuleName(project);
        return moduleName == null ? null : parse(moduleName, namespace);
    }

    static @Nullable ResolvedNamespace parse(@NotNull String moduleName, @NotNull String namespace) {
        if (namespace.startsWith("main.")) {
            String interfaceName = namespace.substring("main.".length()).trim();
            if (interfaceName.isEmpty()) {
                return null;
            }
            return new ResolvedNamespace(namespace, interfaceName, "", true);
        }

        if (!namespace.startsWith(moduleName)) {
            return null;
        }

        String relativeNamespace = namespace.substring(moduleName.length());
        if (relativeNamespace.startsWith(".")) {
            relativeNamespace = relativeNamespace.substring(1);
        }

        String[] parts = relativeNamespace.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        String interfaceName = parts[parts.length - 1].trim();
        if (interfaceName.isEmpty()) {
            return null;
        }

        String relativeDirPath = String.join("/", java.util.Arrays.copyOf(parts, parts.length - 1));
        return new ResolvedNamespace(namespace, interfaceName, relativeDirPath, false);
    }

    public static @NotNull Collection<GoTypeSpec> findTypes(@NotNull Project project,
                                                            @NotNull ResolvedNamespace namespace,
                                                            boolean interfacesOnly) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        Collection<GoTypeSpec> candidates = StubIndex.getElements(
                GoTypesIndex.KEY, namespace.interfaceName(), project, scope, GoTypeSpec.class);

        List<GoTypeSpec> matches = new ArrayList<>();
        for (GoTypeSpec candidate : candidates) {
            if (!matchesNamespace(project, candidate, namespace)) {
                continue;
            }
            if (interfacesOnly && !isInterfaceType(candidate)) {
                continue;
            }
            matches.add(candidate);
        }
        return matches;
    }

    public static @Nullable GoMethodSpec resolveMethod(@NotNull Project project,
                                                       @NotNull ResolvedNamespace namespace,
                                                       @NotNull String methodName) {
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        Collection<GoMethodSpec> matchedMethods = new ArrayList<>();
        StubIndex.getInstance().getAllKeys(GoMethodSpecFingerprintIndex.KEY, project).stream()
                .filter(key -> key.startsWith(methodName + "/"))
                .forEach(key -> matchedMethods.addAll(
                        StubIndex.getElements(GoMethodSpecFingerprintIndex.KEY, key, project, scope, GoMethodSpec.class)));

        for (GoMethodSpec method : matchedMethods) {
            GoTypeSpec typeSpec = PsiTreeUtil.getParentOfType(method, GoTypeSpec.class);
            if (typeSpec == null) {
                continue;
            }
            if (!namespace.interfaceName().equals(typeSpec.getName())) {
                continue;
            }
            if (matchesNamespace(project, typeSpec, namespace)) {
                return method;
            }
        }
        return null;
    }

    public static @NotNull List<String> findMethodNames(@NotNull Project project, @NotNull ResolvedNamespace namespace) {
        Set<String> names = new LinkedHashSet<>();
        for (GoTypeSpec typeSpec : findTypes(project, namespace, true)) {
            typeSpec.getAllMethods().stream()
                    .map(method -> method.getIdentifier().getText())
                    .filter(Objects::nonNull)
                    .forEach(names::add);
        }
        return List.copyOf(names);
    }

    public static boolean matchesNamespace(@NotNull Project project,
                                           @NotNull GoTypeSpec typeSpec,
                                           @NotNull ResolvedNamespace namespace) {
        if (!namespace.interfaceName().equals(typeSpec.getName())) {
            return false;
        }

        PsiFile file = typeSpec.getContainingFile();
        if (!(file instanceof GoFile goFile)) {
            return false;
        }

        if (namespace.mainPackage()) {
            return "main".equals(goFile.getPackageName());
        }

        VirtualFile parent = file.getVirtualFile() == null ? null : file.getVirtualFile().getParent();
        String basePath = project.getBasePath();
        if (parent == null || basePath == null) {
            return false;
        }

        String parentPath = parent.getPath();
        if (!parentPath.startsWith(basePath)) {
            return false;
        }

        String relativeDirPath = parentPath.substring(basePath.length());
        if (relativeDirPath.startsWith("/")) {
            relativeDirPath = relativeDirPath.substring(1);
        }
        return namespace.relativeDirPath().equals(relativeDirPath);
    }

    static boolean isInterfaceType(@NotNull GoTypeSpec typeSpec) {
        String text = typeSpec.getText();
        return text != null
                && text.contains("interface")
                && text.contains("{")
                && text.indexOf("interface") < text.indexOf("{");
    }

    public record ResolvedNamespace(
            @NotNull String original,
            @NotNull String interfaceName,
            @NotNull String relativeDirPath,
            boolean mainPackage
    ) {
    }
}
