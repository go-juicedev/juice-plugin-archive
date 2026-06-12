package com.github.eatmoreapple.juice.injection;

import com.intellij.openapi.util.TextRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapperParamSupportTest {
    @Test
    void findsParameterRanges() {
        List<TextRange> ranges = MapperParamSupport.findParamRanges("where id = #{id} and name = ${name}");

        assertEquals(List.of(
                new TextRange(11, 16),
                new TextRange(28, 35)
        ), ranges);
    }

    @Test
    void keepsWholeSqlWhenThereAreNoParams() {
        List<MapperParamSupport.SqlFragment> fragments = MapperParamSupport.buildSqlFragments("select * from images");

        assertEquals(1, fragments.size());
        assertEquals(new TextRange(0, 20), fragments.get(0).range());
        assertEquals(null, fragments.get(0).prefix());
        assertEquals(null, fragments.get(0).suffix());
    }

    @Test
    void splitsSqlAroundParameters() {
        String sql = "where id = #{id} and deleted = 0";
        List<MapperParamSupport.SqlFragment> fragments = MapperParamSupport.buildSqlFragments(sql);

        assertEquals(2, fragments.size());
        assertEquals(new TextRange(0, 11), fragments.get(0).range());
        assertEquals(null, fragments.get(0).prefix());
        assertEquals(" ? ", fragments.get(0).suffix());
        assertEquals(new TextRange(16, sql.length()), fragments.get(1).range());
        assertEquals(null, fragments.get(1).prefix());
        assertEquals(null, fragments.get(1).suffix());
    }

    @Test
    void carriesReplacementAcrossAdjacentParameters() {
        String sql = "#{id}${name} order by created_at";
        List<MapperParamSupport.SqlFragment> fragments = MapperParamSupport.buildSqlFragments(sql);

        assertEquals(1, fragments.size());
        assertEquals(new TextRange(12, sql.length()), fragments.get(0).range());
        assertEquals(" ?  juice_param ", fragments.get(0).prefix());
        assertEquals(null, fragments.get(0).suffix());
    }
}
