package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.QualityEvidenceModels.Revision;

@Mapper
public interface QualityRevisionMapper {
    @Select("SELECT * FROM source_quality_revision WHERE previous_report_id=#{id}") Revision next(Long id);
    @Select("SELECT * FROM source_quality_revision WHERE replacement_report_id=#{id}") Revision previous(Long id);
    @Insert("INSERT INTO source_quality_revision(previous_report_id,replacement_report_id,reason,recorded_by) VALUES(#{previousReportId},#{replacementReportId},#{reason},#{recordedBy})") int insert(Revision revision);
    @Select("SELECT r.previous_report_id,r.replacement_report_id,p.report_no AS previous_report_no,n.report_no AS replacement_report_no,p.manifest_hash AS previous_hash,n.manifest_hash AS replacement_hash FROM source_quality_revision r JOIN source_quality_report p ON p.id=r.previous_report_id JOIN source_quality_report n ON n.id=r.replacement_report_id WHERE (r.previous_report_id=#{id} OR r.replacement_report_id=#{id}) AND p.published_at IS NOT NULL AND n.published_at IS NOT NULL AND p.organization_id=n.organization_id AND p.scope_type=n.scope_type AND p.harvest_id=n.harvest_id AND (p.packing_id <=> n.packing_id) ORDER BY r.previous_report_id") List<Revision> publicLinks(Long id);
}
