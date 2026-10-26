package org.nongchanpin.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.OrganizationEntity;

@Mapper
public interface OrganizationMapper {
    String FILTER = " WHERE (#{status}='ALL' AND status<>'DELETED' OR status=#{status}) AND (#{search}='' OR LOCATE(#{search},CONCAT_WS(' ',org_code,org_name,contact_name,contact_phone,address))>0) ";
    @Select("SELECT COUNT(*) FROM sys_organization" + FILTER)
    long count(@Param("search") String search, @Param("status") String status);
    @Select("SELECT * FROM sys_organization" + FILTER + " ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<OrganizationEntity> list(@Param("search") String search, @Param("status") String status, @Param("size") int size, @Param("offset") long offset);
    @Select("SELECT * FROM sys_organization WHERE id=#{id}") OrganizationEntity get(Long id);
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    @Select("SELECT * FROM sys_organization WHERE id=#{id} FOR UPDATE") OrganizationEntity lock(Long id);
    @Insert("INSERT INTO sys_organization(org_code,org_name,org_type,contact_name,contact_phone,address) VALUES(#{orgCode},#{orgName},#{orgType},#{contactName},#{contactPhone},#{address})")
    @Options(useGeneratedKeys = true, keyProperty = "id") int insert(OrganizationEntity row);
    @Update("UPDATE sys_organization SET org_name=#{orgName},org_type=#{orgType},contact_name=#{contactName},contact_phone=#{contactPhone},address=#{address} WHERE id=#{id}") int update(OrganizationEntity row);
    @Update("UPDATE sys_organization SET status=#{status} WHERE id=#{id}") int status(@Param("id") Long id, @Param("status") String status);
    // Count every direct organization reference, including disabled accounts and archived business facts.
    @Select("SELECT '人员账号' label,COUNT(*) total FROM sys_user WHERE organization_id=#{id} UNION ALL "
            + "SELECT '产品',COUNT(*) FROM product WHERE organization_id=#{id} UNION ALL "
            + "SELECT '批次',COUNT(*) FROM product_batch WHERE organization_id=#{id} UNION ALL "
            + "SELECT '地块',COUNT(*) FROM farm_plot WHERE organization_id=#{id} UNION ALL "
            + "SELECT '设备归属',COUNT(*) FROM device_ownership WHERE organization_id=#{id} UNION ALL "
            + "SELECT '仓库',COUNT(*) FROM inventory_warehouse WHERE organization_id=#{id} UNION ALL "
            + "SELECT '销售订单',COUNT(*) FROM sales_order WHERE organization_id=#{id} UNION ALL "
            + "SELECT '质检报告',COUNT(*) FROM source_quality_report WHERE organization_id=#{id} UNION ALL "
            + "SELECT '退回处置',COUNT(*) FROM rejection_case WHERE organization_id=#{id} UNION ALL "
            + "SELECT '订单替代记录',COUNT(*) FROM sales_order_replacement WHERE organization_id=#{id} UNION ALL "
            + "SELECT '操作审计',COUNT(*) FROM operation_audit WHERE organization_id=#{id}")
    List<Map<String, Object>> dependencies(Long id);
}
