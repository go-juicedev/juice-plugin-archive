package com.github.eatmoreapple.juice.completion;

import com.goide.psi.GoTypeSpec;
import com.goide.stubs.index.GoTypesIndex;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author pjh
 * @date 2025/1/24
 */
public class SqlIdCompletionContributor extends CompletionContributor {
    private static final Logger log = LoggerFactory.getLogger(SqlIdCompletionContributor.class);

    public SqlIdCompletionContributor() {
        // 为 id 属性添加自动完成
        extend(CompletionType.BASIC,
                PlatformPatterns.psiElement().inside(
                        XmlPatterns.xmlAttribute().withName("id").withParent(
                                XmlPatterns.xmlTag().withName(
                                        PlatformPatterns.string().oneOf("select", "insert", "update", "delete")
                                )
                        )
                ),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        try {
                            PsiElement position = parameters.getPosition();

                            // 向上查找到 XmlAttribute
                            PsiElement current = position;
                            while (current != null && !(current instanceof XmlAttribute)) {
                                current = current.getParent();
                            }

                            if (!(current instanceof XmlAttribute)) {
                                return;
                            }

                            XmlAttribute attribute = (XmlAttribute) current;
                            XmlTag tag = attribute.getParent();

                            if (tag == null) {
                                return;
                            }

                            // 获取 mapper 标签
                            XmlTag mapperTag = tag.getParentTag();
                            if (mapperTag == null || !mapperTag.getName().equals("mapper")) {
                                return;
                            }

                            // 获取 namespace
                            String namespace = mapperTag.getAttributeValue("namespace");
                            if (namespace == null || namespace.isEmpty()) {
                                return;
                            }

                            // 获取当前项目的 module name
                            String moduleName = getModuleName(position.getProject());
                            if (moduleName == null || moduleName.isEmpty()) {
                                return;
                            }

                            // 去掉 namespace 中的 module name 前缀
                            String relativeNamespace = namespace;
                            if (namespace.startsWith(moduleName)) {
                                relativeNamespace = namespace.substring(moduleName.length() + 1); // 去掉前缀和后面的 "."
                            }

                            // 解析 relativeNamespace 获取包路径和接口名
                            String[] parts = relativeNamespace.split("\\.");
                            if (parts.length < 2) {
                                return; // 格式不正确
                            }
                            String packagePath = parts[parts.length - 2]; // 包路径
                            String interfaceName = parts[parts.length - 1]; // 接口名

                            // 查找匹配的 GoTypeSpec
                            GlobalSearchScope globalSearchScope = GlobalSearchScope.allScope(position.getProject());
                            Collection<GoTypeSpec> elements = StubIndex.getElements(
                                    GoTypesIndex.KEY, interfaceName, position.getProject(), globalSearchScope, GoTypeSpec.class);

                            // 过滤出包路径和接口名匹配的 GoTypeSpec
                            Set<String> resultMethods = elements.stream()
                                    .filter(element -> {
                                        // 检查包路径是否匹配
                                        String elementPackagePath = element.getContainingFile().getPackageName();
                                        return elementPackagePath.endsWith(packagePath); // 检查包路径是否以目标包路径结尾
                                    })
                                    .flatMap(element -> element.getAllMethods().stream()
                                            .map(method -> method.getIdentifier().getText()))
                                    .collect(Collectors.toSet());

                            // 添加方法名到自动完成列表
                            resultMethods.forEach(resultMethod -> result.addElement(
                                    LookupElementBuilder.create(resultMethod)
                                            .withLookupString(resultMethod.toLowerCase())
                            ));
                        } catch (Exception e) {
                            log.warn("Error in completion contributor", e);
                        }
                    }
                }
        );
    }

    /**
     * 获取当前项目的 module name
     */
    @Nullable
    private String getModuleName(Project project) {
        // 获取项目的根目录
        String basePath = project.getBasePath();
        if (basePath == null) {
            return null;
        }

        // 找到 go.mod 文件
        VirtualFile goModFile = project.getBaseDir().findChild("go.mod");
        if (goModFile == null || !goModFile.exists()) {
            return null; // go.mod 文件不存在
        }

        // 读取 go.mod 文件内容
        try (InputStream inputStream = goModFile.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            Pattern modulePattern = Pattern.compile("^module\\s+(.+)$");
            while ((line = reader.readLine()) != null) {
                // 匹配 module 指令
                Matcher matcher = modulePattern.matcher(line.trim());
                if (matcher.matches()) {
                    return matcher.group(1).replace("/", "."); // 返回 module name
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read go.mod file", e);
        }

        return null; // 未找到 module 指令
    }
}