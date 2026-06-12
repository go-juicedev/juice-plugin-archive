package com.github.eatmoreapple.juice.marker;

import com.goide.psi.*;
import com.github.eatmoreapple.juice.resolve.MapperNamespaceResolver;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GoMethodLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                          @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof GoMethodSpec methodSpec)) {
            return;
        }

        String methodName = methodSpec.getIdentifier().getText();
        PsiElement identifier = methodSpec.getIdentifier();
        GoTypeSpec typeSpec = PsiTreeUtil.getParentOfType(methodSpec, GoTypeSpec.class);
        if (typeSpec == null) {
            return;
        }

        try {
            PsiFile containingFile = element.getContainingFile();
            if (!(containingFile instanceof GoFile)) {
                return;
            }

            Collection<VirtualFile> xmlFiles = FilenameIndex.getAllFilesByExt(
                    element.getProject(), "xml", GlobalSearchScope.projectScope(element.getProject()));

            List<PsiElement> targets = new ArrayList<>();

            for (VirtualFile xmlFile : xmlFiles) {
                PsiFile psiFile = PsiManager.getInstance(element.getProject()).findFile(xmlFile);
                if (!(psiFile instanceof XmlFile)) {
                    continue;
                }

                XmlTag rootTag = ((XmlFile) psiFile).getRootTag();
                if (rootTag == null || !rootTag.getName().equals("mapper")) {
                    continue;
                }

                String xmlNamespace = rootTag.getAttributeValue("namespace");
                if (xmlNamespace == null) {
                    continue;
                }

                MapperNamespaceResolver.ResolvedNamespace resolvedNamespace =
                        MapperNamespaceResolver.parse(element.getProject(), xmlNamespace);
                if (resolvedNamespace == null) {
                    continue;
                }

                if (!MapperNamespaceResolver.matchesNamespace(element.getProject(), typeSpec, resolvedNamespace)) {
                    continue;
                }

                for (XmlTag tag : rootTag.getSubTags()) {
                    if (tag.getName().equals("select") || tag.getName().equals("insert") ||
                            tag.getName().equals("update") || tag.getName().equals("delete")) {
                        XmlAttribute idAttr = tag.getAttribute("id");
                        if (idAttr != null && idAttr.getValue() != null && 
                                idAttr.getValue().equals(methodName)) {
                            targets.add(tag);
                        }
                    }
                }
            }

            if (!targets.isEmpty()) {
                NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                        .create(AllIcons.FileTypes.Xml)
                        .setTargets(targets)
                        .setTooltipText("Navigate to XML mapper");
                result.add(builder.createLineMarkerInfo(identifier));
            }
        } catch (Exception e) {
            // Avoid breaking editor highlighting if resolution fails.
        }
    }
}
