package com.github.eatmoreapple.juice.util;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author pjh
 * @date 2025/1/24
 */
public enum Icons {

    JUICE_ICON(IconLoader.getIcon("/icons/juice.svg"));

    private Icon icon;

    Icons(Icon icon) {
        this.icon = icon;
    }

    public Icon getIcon() {
        return icon;
    }

}
