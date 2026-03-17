package com.github.eatmoreapple.juice.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MapperParamTokenType extends IElementType {
    public static final MapperParamTokenType HASH_PARAM = new MapperParamTokenType("HASH_PARAM");
    public static final MapperParamTokenType DOLLAR_PARAM = new MapperParamTokenType("DOLLAR_PARAM");
    public static final MapperParamTokenType TEXT = new MapperParamTokenType("TEXT");

    private MapperParamTokenType(@NotNull @NonNls String debugName) {
        super(debugName, MapperParamLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "MapperParamTokenType." + super.toString();
    }
}
