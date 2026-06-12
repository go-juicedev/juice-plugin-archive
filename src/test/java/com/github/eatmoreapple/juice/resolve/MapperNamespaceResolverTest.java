package com.github.eatmoreapple.juice.resolve;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapperNamespaceResolverTest {
    @Test
    void parsesModuleNamespaceIntoDirectoryAndInterface() {
        MapperNamespaceResolver.ResolvedNamespace namespace =
                MapperNamespaceResolver.parse("github.com.demo.project", "github.com.demo.project.user.repo.UserMapper");

        assertNotNull(namespace);
        assertEquals("UserMapper", namespace.interfaceName());
        assertEquals("user/repo", namespace.relativeDirPath());
        assertFalse(namespace.mainPackage());
    }

    @Test
    void parsesMainNamespace() {
        MapperNamespaceResolver.ResolvedNamespace namespace =
                MapperNamespaceResolver.parse("github.com.demo.project", "main.UserMapper");

        assertNotNull(namespace);
        assertEquals("UserMapper", namespace.interfaceName());
        assertEquals("", namespace.relativeDirPath());
        assertTrue(namespace.mainPackage());
    }

    @Test
    void rejectsNamespaceOutsideCurrentModule() {
        assertNull(MapperNamespaceResolver.parse("github.com.demo.project", "github.com.other.project.UserMapper"));
    }

    @Test
    void rejectsNamespaceMissingPackagePath() {
        assertNull(MapperNamespaceResolver.parse("github.com.demo.project", "github.com.demo.project.UserMapper"));
    }
}
