package com.github.eatmoreapple.juice.marker;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

public class MapperResourceLineMarkerProvider extends RelatedItemLineMarkerProvider {
    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                          @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof XmlTag)) {
            return;
        }

        XmlTag tag = (XmlTag) element;
        
        // Check if this is a mapper tag
        if (!"mapper".equals(tag.getName())) {
            return;
        }

        // Get the resource attribute
        String resource = tag.getAttributeValue("resource");
        if (resource == null || resource.isEmpty()) {
            return;
        }

        // Find the juice.xml file's directory
        PsiFile containingFile = element.getContainingFile();
        VirtualFile juiceXmlDir = containingFile.getVirtualFile().getParent();
        if (juiceXmlDir == null) {
            return;
        }

        // Construct the path to the target mapper file
        String mapperPath = juiceXmlDir.getPath() + "/" + resource;
        VirtualFile mapperFile = LocalFileSystem.getInstance().findFileByPath(mapperPath);
        if (mapperFile == null) {
            return;
        }

        // Get the PSI file for the mapper
        PsiFile targetFile = PsiManager.getInstance(element.getProject()).findFile(mapperFile);
        if (targetFile == null) {
            return;
        }

        // Create the navigation marker
        NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder
                .create(AllIcons.FileTypes.Xml)
                .setTarget(targetFile)
                .setTooltipText("Navigate to mapper file");

        result.add(builder.createLineMarkerInfo(tag));
    }
}
