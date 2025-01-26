package com.github.eatmoreapple.juice.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;

public class GoModuleUtil {
    private static final Logger log = LoggerFactory.getLogger(GoModuleUtil.class);

    /**
     * 从 namespace 中解析出包路径和接口名
     * @param namespace 完整的命名空间
     * @param project 当前项目
     * @return 包含包路径和接口名的数组，如果解析失败返回 null
     */
    public static String[] parseNamespace(String namespace, Project project) {
        try {
            // 查找 go.mod 文件
            Collection<VirtualFile> goModFiles = FilenameIndex.getVirtualFilesByName(
                    "go.mod",
                    GlobalSearchScope.projectScope(project)
            );

            if (goModFiles.isEmpty()) {
                log.warn("No go.mod file found in project");
                return null;
            }

            // 读取 go.mod 文件获取模块名
            String moduleName = null;
            for (VirtualFile goModFile : goModFiles) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(goModFile.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("module ")) {
                            moduleName = line.substring("module ".length()).trim();
                            break;
                        }
                    }
                }
                if (moduleName != null) {
                    break;
                }
            }

            if (moduleName == null) {
                log.warn("No module name found in go.mod");
                return null;
            }

            // 如果 namespace 不是以模块名开头，返回 null
            if (!namespace.startsWith(moduleName)) {
                log.warn("Namespace {} does not start with module name {}", namespace, moduleName);
                return null;
            }

            // 去掉模块名部分
            String remaining = namespace.substring(moduleName.length());
            if (remaining.startsWith("/")) {
                remaining = remaining.substring(1);
            }

            // 分割剩余部分获取包路径和接口名
            int lastDotIndex = remaining.lastIndexOf('.');
            if (lastDotIndex == -1) {
                log.warn("No interface name found in namespace");
                return null;
            }

            String packagePath = remaining.substring(0, lastDotIndex);
            String interfaceName = remaining.substring(lastDotIndex + 1);

            return new String[]{packagePath, interfaceName};
        } catch (IOException e) {
            log.warn("Error parsing namespace", e);
            return null;
        }
    }
}
