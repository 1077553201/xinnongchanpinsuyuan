package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.SourceQualityModels.*;

@Mapper
public interface SourceQualityMapper {
    String SOURCE="SELECT f.organization_id,h.id AS harvest_id,h.lot_code AS harvest_code,h.harvested_at,c.cycle_code,c.product_name,c.variety ";
    String JOINS=" FROM harvest_lot h JOIN planting_cycle c ON c.id=h.cycle_id JOIN farm_plot f ON f.id=c.plot_id ";
    @Select(SOURCE+JOINS+"WHERE h.id=#{id}") Source harvest(Long id);
    @Select("SELECT p.id AS packing_id,p.packing_code,p.packed_at,f.organization_id,h.id AS harvest_id,h.lot_code AS harvest_code,h.harvested_at,c.cycle_code,c.product_name,c.variety FROM packing_lot p JOIN harvest_grade g ON g.id=p.grade_id JOIN harvest_lot h ON h.id=g.harvest_id JOIN planting_cycle c ON c.id=h.cycle_id JOIN farm_plot f ON f.id=c.plot_id WHERE p.id=#{id}") Source packing(Long id);
    String FILTER=" WHERE scope_type=#{scope} AND ((#{scope}='HARVEST' AND harvest_id=#{id}) OR (#{scope}='PACKING' AND packing_id=#{id})) ";
    @Select("SELECT COUNT(*) FROM source_quality_report"+FILTER) long count(@Param("scope")String scope,@Param("id")Long id);
    @Select("SELECT * FROM source_quality_report"+FILTER+" ORDER BY id DESC LIMIT 20 OFFSET #{offset}") List<Report> list(@Param("scope")String scope,@Param("id")Long id,@Param("offset")long offset);
    @Select("SELECT * FROM source_quality_report WHERE id=#{id}") Report report(Long id);
    @Select("SELECT * FROM source_quality_report WHERE id=#{id} FOR UPDATE") Report lock(Long id);
    @Select("SELECT * FROM source_quality_report WHERE request_key=#{key}") Report byRequest(String key);
    @Select("SELECT id FROM harvest_lot WHERE id=#{id} FOR UPDATE") Long lockHarvest(Long id);
    @Insert("INSERT INTO source_quality_report(organization_id,scope_type,harvest_id,packing_id,report_no,agency_name,sampled_on,checked_on,declared_result,file_name,file_type,file_size,file_sha256,manifest_json,manifest_hash,recorded_by,recorder_name,request_key,request_hash) VALUES(#{organizationId},#{scopeType},#{harvestId},#{packingId},#{reportNo},#{agencyName},#{sampledOn},#{checkedOn},#{declaredResult},#{fileName},#{fileType},#{fileSize},#{fileSha256},#{manifestJson},#{manifestHash},#{recordedBy},#{recorderName},#{requestKey},#{requestHash})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(Report report);
    @Insert("INSERT INTO source_quality_attachment(report_id,content) VALUES(#{id},#{content})") int attachment(@Param("id")Long id,@Param("content")byte[] content);
    @Select("SELECT content FROM source_quality_attachment WHERE report_id=#{id}") Attachment content(Long id);
    @Update("UPDATE source_quality_report SET status='PUBLISHED',attachment_public=#{attachmentPublic},published_by=#{publishedBy},publisher_name=#{publisherName},published_at=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND status='DRAFT'") int publish(Report report);
    @Update("UPDATE source_quality_report SET status='WITHDRAWN',attachment_public=FALSE,withdrawn_by=#{withdrawnBy},withdrawn_at=CURRENT_TIMESTAMP(6),withdraw_reason=#{withdrawReason} WHERE id=#{id} AND status IN ('DRAFT','PUBLISHED')") int withdraw(Report report);
    String PUBLIC_FILTER=" WHERE published_at IS NOT NULL AND status IN ('PUBLISHED','WITHDRAWN') AND ((scope_type='PACKING' AND packing_id=#{packing}) OR (scope_type='HARVEST' AND harvest_id=#{harvest})) ";
    @Select("SELECT COUNT(*) FROM source_quality_report"+PUBLIC_FILTER) long publicCount(@Param("packing")Long packing,@Param("harvest")Long harvest);
    @Select("SELECT * FROM source_quality_report"+PUBLIC_FILTER+" ORDER BY published_at DESC,id DESC LIMIT 50") List<Report> publicReports(@Param("packing")Long packing,@Param("harvest")Long harvest);
}
