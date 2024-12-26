package com.github.eatmoreapple.juice.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapperResourceResolver {
    private final Project project;
    private final PsiManager psiManager;

    public MapperResourceResolver(Project project) {
        this.project = project;
        this.psiManager = PsiManager.getInstance(project);
    }

    public List<PsiFile> resolveMapperFiles(XmlAttributeValue resourceValue) {
        List<PsiFile> results = new ArrayList<>();
        
        // Get the base directory
        PsiFile containingFile = resourceValue.getContainingFile();
        if (!(containingFile instanceof XmlFile)) {
            return results;
        }

        VirtualFile baseDir = containingFile.getVirtualFile().getParent();
        if (baseDir == null) {
            return results;
        }

        // Create file references
        FileReferenceSet referenceSet = new FileReferenceSet(resourceValue.getValue(), resourceValue,
                1, null, true, true);

        // Get all possible files
        for (PsiReference ref : referenceSet.getAllReferences()) {
            Object[] variants = ref.getVariants();
            for (Object variant : variants) {
                if (variant instanceof PsiFile && ((PsiFile) variant).getName().endsWith(".xml")) {
                    results.add((PsiFile) variant);
                }
            }
        }

        return results;
    }
}
