package com.example.blogger.mapper;

import com.example.blogger.entity.ExportTemplate;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ExportTemplateMapper {

    @Select("SELECT * FROM tu_export_template WHERE is_deleted = 0 ORDER BY created_at DESC")
    List<ExportTemplate> findAll();

    @Select("SELECT * FROM tu_export_template WHERE id = #{id} AND is_deleted = 0")
    ExportTemplate findById(String id);

    @Select("SELECT * FROM tu_export_template WHERE name = #{name} AND is_deleted = 0 LIMIT 1")
    ExportTemplate findByName(String name);

    @Select("SELECT * FROM tu_export_template WHERE is_default = 1 AND is_deleted = 0 LIMIT 1")
    ExportTemplate findDefault();

    @Insert("INSERT INTO tu_export_template(id, name, type, config, is_default, is_deleted, created_at, updated_at) " +
            "VALUES(#{id}, #{name}, #{type}, #{config}, #{isDefault}, 0, NOW(), NOW())")
    int insert(ExportTemplate template);

    @Update("UPDATE tu_export_template SET name=#{name}, type=#{type}, config=#{config}, is_default=#{isDefault}, updated_at=NOW() WHERE id=#{id}")
    int update(ExportTemplate template);

    @Update("UPDATE tu_export_template SET is_default = 0")
    int clearDefault();

    @Update("UPDATE tu_export_template SET is_deleted = 1 WHERE id = #{id}")
    int delete(String id);
}
