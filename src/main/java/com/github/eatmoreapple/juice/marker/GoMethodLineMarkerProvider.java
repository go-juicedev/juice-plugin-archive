package com.github.eatmoreapple.juice.marker;

import com.goide.psi.*;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GoMethodLineMarkerProvider extends RelatedItemLineMarkerProvider {
    private static final Logger log = LoggerFactory.getLogger(GoMethodLineMarkerProvider.class);

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                          @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        log.warn("Checking element: " + element.getText() + ", class: " + element.getClass().getName());
        
        // 获取方法名和标识符
        String methodName;
        PsiElement identifier;
        PsiElement parent;

        if (element instanceof GoMethodSpec) {
            GoMethodSpec methodSpec = (GoMethodSpec) element;
            methodName = methodSpec.getIdentifier().getText();
            identifier = methodSpec.getIdentifier();
            // 获取包含此方法的接口定义
            PsiElement parentElement = methodSpec.getParent();
            while (parentElement != null && !(parentElement instanceof GoTypeSpec)) {
                parentElement = parentElement.getParent();
            }
            if (!(parentElement instanceof GoTypeSpec)) {
                log.warn("Could not find parent GoTypeSpec");
                return;
            }
            parent = parentElement;
            log.warn("Found method spec: " + methodName);
        } else {
            return;
        }

        try {
            GoTypeSpec typeSpec = (GoTypeSpec) parent;
            String interfaceName = typeSpec.getIdentifier().getText();
            log.warn("Found method: " + methodName + " in interface: " + interfaceName);

            // 获取包路径
            PsiFile containingFile = element.getContainingFile();
            if (!(containingFile instanceof GoFile)) {
                log.warn("Containing file is not a Go file");
                return;
            }
            String packagePath = ((GoFile) containingFile).getPackageName();
            if (packagePath == null) {
                log.warn("Package path is null");
                return;
            }
            log.warn("Package path: " + packagePath);

            // 获取模块名
            String moduleName = getModuleName(element.getProject());
            if (moduleName == null) {
                log.warn("Module name is null");
                return;
            }
            log.warn("Module name: " + moduleName);

            // 在项目中查找所有 XML 文件
            Collection<VirtualFile> xmlFiles = FilenameIndex.getAllFilesByExt(
                    element.getProject(), "xml", GlobalSearchScope.projectScope(element.getProject()));
            log.warn("Found " + xmlFiles.size() + " XML files");

            List<PsiElement> targets = new ArrayList<>();

            // 遍历 XML 文件查找匹配的标签
            for (VirtualFile xmlFile : xmlFiles) {
                log.warn("Checking XML file: " + xmlFile.getPath());
                PsiFile psiFile = PsiManager.getInstance(element.getProject()).findFile(xmlFile);
                if (!(psiFile instanceof XmlFile)) {
                    continue;
                }

                XmlTag rootTag = ((XmlFile) psiFile).getRootTag();
                if (rootTag == null || !rootTag.getName().equals("mapper")) {
                    continue;
                }

                // 检查 namespace 是否匹配
                String xmlNamespace = rootTag.getAttributeValue("namespace");
                if (xmlNamespace == null) {
                    continue;
                }
                log.warn("XML namespace: " + xmlNamespace);

                // 去掉 namespace 中的 module name 前缀
                String relativeNamespace = xmlNamespace;
                if (xmlNamespace.startsWith(moduleName)) {
                    relativeNamespace = xmlNamespace.substring(moduleName.length() + 1); // 去掉前缀和后面的 "."
                }
                log.warn("Relative namespace: " + relativeNamespace);

                // 解析 namespace
                String[] parts = relativeNamespace.split("\\.");
                if (parts.length < 2) {
                    log.warn("Invalid namespace format: parts.length < 2");
                    continue;
                }

                // 获取 namespace 中的包路径和接口名
                String nsInterfaceName = parts[parts.length - 1];
                // 将除了最后一个部分以外的所有部分作为包路径
                StringBuilder nsPackagePathBuilder = new StringBuilder(parts[0]);
                for (int i = 1; i < parts.length - 1; i++) {
                    nsPackagePathBuilder.append("/").append(parts[i]);
                }
                String nsPackagePath = nsPackagePathBuilder.toString();
                log.warn("Namespace package path: " + nsPackagePath);
                log.warn("Namespace interface name: " + nsInterfaceName);
                log.warn("Current package path: " + packagePath);
                log.warn("Current interface name: " + interfaceName);

                // 检查接口名和包路径是否匹配
                boolean interfaceMatch = interfaceName.equals(nsInterfaceName);
                boolean packageMatch = packagePath.endsWith(nsPackagePath);
                log.warn("Interface match: " + interfaceMatch + ", Package match: " + packageMatch);

                if (!interfaceMatch || !packageMatch) {
                    continue;
                }

                // 查找所有 SQL 标签
                for (XmlTag tag : rootTag.getSubTags()) {
                    if (tag.getName().equals("select") || tag.getName().equals("insert") ||
                            tag.getName().equals("update") || tag.getName().equals("delete")) {
                        XmlAttribute idAttr = tag.getAttribute("id");
                        if (idAttr != null && idAttr.getValue() != null && 
                                idAttr.getValue().equals(methodName)) {
                            log.warn("Found matching tag: " + tag.getName() + " with id: " + methodName);
                            targets.add(tag);
                        }
                    }
                }
            }

            // 如果找到匹配的 XML 标签，添加行标记
            if (!targets.isEmpty()) {
                log.warn("Adding line marker for " + targets.size() + " targets");
                NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                        .create(AllIcons.FileTypes.Xml)  // 使用 XML 图标
                        .setTargets(targets)
                        .setTooltipText("Navigate to XML mapper");
                result.add(builder.createLineMarkerInfo(identifier));
            } else {
                log.warn("No targets found");
            }
        } catch (Exception e) {
            log.warn("Error in GoMethodLineMarkerProvider", e);
        }
    }

    private String getModuleName(Project project) {
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
                    if (line.startsWith("module ")) {
                        String moduleName = line.substring("module ".length()).trim();
                        log.warn("Found module name: " + moduleName);
                        return moduleName;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read go.mod file", e);
        }
        return null;
    }
}
