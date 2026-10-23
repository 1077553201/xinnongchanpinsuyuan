package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.nongchanpin.model.entity.TraceItemEntity;

@Mapper
public interface TraceItemMapper {
    @Select("SELECT b.* FROM packing_trace_batch b JOIN packing_trace_item i ON i.packing_id=b.packing_id WHERE i.item_id=#{id}") org.nongchanpin.model.entity.PackingTraceModels.Bridge packingSource(Long id);
    @Select("SELECT COUNT(*) FROM packing_trace_batch WHERE batch_id=#{id}") int packingBatchCount(Long id);
    @Insert("INSERT INTO trace_item (batch_id, trace_code_hash, trace_code_encrypted, trace_code_plain, trace_url, qr_status, nfc_status, chain_status, status) "
            + "VALUES (#{batchId}, #{traceCodeHash}, #{traceCodeEncrypted}, #{traceCodePlain}, #{traceUrl}, #{qrStatus}, #{nfcStatus}, #{chainStatus}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TraceItemEntity item);

    @Select("SELECT * FROM trace_item WHERE status <> 'DELETED' ORDER BY id DESC")
    List<TraceItemEntity> findAll();

    @Select("SELECT * FROM trace_item WHERE id = #{id}")
    TraceItemEntity findById(Long id);

    @Select("SELECT * FROM trace_item WHERE trace_code_hash = #{traceCodeHash}")
    TraceItemEntity findByTraceCodeHash(String traceCodeHash);

    @Select("SELECT COUNT(1) FROM trace_item WHERE trace_code_hash = #{traceCodeHash}")
    int countByTraceCodeHash(String traceCodeHash);

    @Update("UPDATE trace_item SET chain_status = #{chainStatus}, update_time = NOW() WHERE id = #{id}")
    int updateChainStatus(@Param("id") Long id, @Param("chainStatus") String chainStatus);

    @Update("UPDATE trace_item SET nfc_status = #{nfcStatus}, update_time = NOW() WHERE id = #{id}")
    int updateNfcStatus(@Param("id") Long id, @Param("nfcStatus") String nfcStatus);

    @Update("UPDATE trace_item SET chain_status = #{chainStatus}, status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("chainStatus") String chainStatus, @Param("status") String status);
}
