package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.nongchanpin.model.entity.QualityReportEntity;

@Mapper
public interface QualityReportMapper {
    @Select("SELECT * FROM quality_report WHERE (item_id = #{itemId} OR batch_id = #{batchId}) AND status = 'ACTIVE' ORDER BY checked_at DESC, id DESC")
    List<QualityReportEntity> findForTrace(@Param("itemId") Long itemId, @Param("batchId") Long batchId);
}
