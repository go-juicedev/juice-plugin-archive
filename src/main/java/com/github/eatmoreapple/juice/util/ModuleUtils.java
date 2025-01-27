package com.github.eatmoreapple.juice.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Go 模块工具类
 */
public class ModuleUtils {
    private static final Logger log = Logger.getInstance(ModuleUtils.class);
    private static final Pattern MODULE_PATTERN = Pattern.compile("^module\\s+(.+)$");

    /**
     * 获取项目的模块名
     *
     * @param project 项目实例
     * @return 模块名，如果未找到则返回 null
     */
    @Nullable
    public static String getModuleName(@NotNull Project project) {
        try {
            // 获取项目的根目录
            String basePath = project.getBasePath();
            if (basePath == null) {
                return null;
            }

            // 找到 go.mod 文件
            VirtualFile goModFile = project.getBaseDir().findChild("go.mod");
            if (goModFile == null || !goModFile.exists()) {
                return null;
            }

            // 读取 go.mod 文件内容
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(goModFile.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    Matcher matcher = MODULE_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        String moduleName = matcher.group(1).trim();
                        // 将斜杠替换为点号
                        return moduleName.replace("/", ".");
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Error reading go.mod file", e);
        }
        return null;
    }
}
