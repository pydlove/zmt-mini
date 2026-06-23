package com.example.blogger.mapper;

import com.example.blogger.entity.StyleConfig;
import org.apache.ibatis.annotations.*;

@Mapper
public interface StyleConfigMapper {

    @Select("SELECT * FROM tu_style_config WHERE is_active = 1 LIMIT 1")
    StyleConfig findActive();

    @Insert("INSERT INTO tu_style_config(id, name, strategy, params, is_active, created_at, updated_at) " +
            "VALUES(#{id}, #{name}, #{strategy}, #{params}, #{isActive}, NOW(), NOW())")
    int insert(StyleConfig config);

    @Update("UPDATE tu_style_config SET is_active = 0")
    int deactivateAll();

    @Update("UPDATE tu_style_config SET name = #{name}, strategy = #{strategy}, params = #{params}, " +
            "is_active = #{isActive}, updated_at = NOW() WHERE id = #{id}")
    int update(StyleConfig config);
}
