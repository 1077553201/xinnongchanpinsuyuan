package org.nongchanpin.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.QualityEvidenceModels.Evidence;

@Mapper
public interface QualityEvidenceMapper {
    @Select("SELECT * FROM quality_item_evidence WHERE report_id=#{report} AND item_id=#{item} ORDER BY version DESC LIMIT 1") Evidence latest(@Param("report")Long report,@Param("item")Long item);
    @Select("SELECT * FROM quality_item_evidence WHERE id=#{id}") Evidence find(Long id);
    @Select("SELECT * FROM quality_item_evidence WHERE report_id=#{report} AND item_id=#{item} ORDER BY version DESC LIMIT 20") List<Evidence> versions(@Param("report")Long report,@Param("item")Long item);
    @Select("SELECT * FROM quality_item_evidence WHERE report_id=#{report} AND item_id=#{item} AND version=#{version}") Evidence byVersion(@Param("report")Long report,@Param("item")Long item,@Param("version")int version);
    @Insert("INSERT INTO quality_item_evidence(report_id,item_id,version,manifest_json,manifest_hash,previous_hash,event_id,created_by) VALUES(#{reportId},#{itemId},#{version},#{manifestJson},#{manifestHash},#{previousHash},#{eventId},#{createdBy})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(Evidence evidence);
    @Update("UPDATE quality_item_evidence SET chain_status=#{chainStatus},tx_id=#{txId},error_code=#{errorCode} WHERE id=#{id}") int state(Evidence evidence);
    @Update("UPDATE quality_item_evidence SET lease_token=#{token},lease_until=#{until} WHERE id=#{id}") int claim(@Param("id")Long id,@Param("token")String token,@Param("until")LocalDateTime until);
    @Update("UPDATE quality_item_evidence SET lease_token=NULL,lease_until=NULL WHERE id=#{id} AND lease_token=#{token}") int release(@Param("id")Long id,@Param("token")String token);
}
