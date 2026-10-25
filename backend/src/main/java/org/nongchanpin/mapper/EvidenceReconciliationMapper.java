package org.nongchanpin.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.vo.EvidenceReconciliationRow;

@Mapper
public interface EvidenceReconciliationMapper {
    String UNION="(SELECT 'PRODUCTION' kind,j.id record_id,j.packing_id,IF(j.business_type='ITEM_BIND',j.business_id,NULL) item_id,NULL report_id,1 version,j.business_type action,j.payload_hash frozen_hash,j.chain_status,j.error_code,j.tx_id,e.created_at recorded_at,e.lease_until FROM packing_evidence_job j JOIN packing_evidence e ON e.packing_id=j.packing_id "
        +"UNION ALL SELECT 'CIRCULATION',e.id,i.packing_id,e.item_id,NULL,e.version,'TRACE_EVENT',e.manifest_hash,e.chain_status,e.error_code,e.tx_id,e.created_at,e.lease_until FROM item_circulation_evidence e JOIN packing_trace_item i ON i.item_id=e.item_id "
        +"UNION ALL SELECT 'QUALITY',e.id,i.packing_id,e.item_id,e.report_id,e.version,'QUALITY_TEST',e.manifest_hash,e.chain_status,e.error_code,e.tx_id,e.created_at,e.lease_until FROM quality_item_evidence e JOIN packing_trace_item i ON i.item_id=e.item_id "
        +"UNION ALL SELECT 'NFC',e.id,e.packing_id,e.item_id,NULL,1,e.operation,e.payload_hash,e.chain_status,e.error_code,e.tx_id,e.recorded_at,b.lease_until FROM packing_nfc_operation e JOIN packing_nfc_operation b ON b.item_id=e.item_id AND b.operation='BIND') r ";
    String FROM=" FROM "+UNION+" JOIN packing_lot p ON p.id=r.packing_id JOIN harvest_grade g ON g.id=p.grade_id JOIN harvest_lot h ON h.id=g.harvest_id JOIN planting_cycle c ON c.id=h.cycle_id JOIN farm_plot f ON f.id=c.plot_id LEFT JOIN trace_item t ON t.id=r.item_id LEFT JOIN source_quality_report q ON q.id=r.report_id LEFT JOIN chain_transaction tx ON tx.id=r.tx_id ";
    String COLUMNS="r.*,t.trace_code_plain trace_code,f.organization_id,f.plot_name,p.packing_code,c.id cycle_id,q.report_no,tx.submitted_at,tx.chain_status transaction_status,tx.receipt_status";
    String FILTER=" WHERE (#{org} IS NULL OR f.organization_id=#{org}) AND (#{kind}='' OR r.kind=#{kind}) AND (#{status}='ALL' OR (#{status}='ATTENTION' AND (r.chain_status IN ('NOT_SUBMITTED','PENDING','UNCERTAIN','FAILED','CHAIN_DISABLED') OR tx.chain_status IN ('PENDING','UNCERTAIN'))) OR r.chain_status=#{status}) AND (#{packingId} IS NULL OR r.packing_id=#{packingId}) AND (#{code}='' OR t.trace_code_plain=#{code}) AND COALESCE(tx.submitted_at,r.recorded_at)&lt;=#{before} ";
    @Select("<script>SELECT "+COLUMNS+FROM+FILTER+" ORDER BY COALESCE(tx.submitted_at,r.recorded_at),r.kind,r.record_id LIMIT 20 OFFSET #{offset}</script>")
    List<EvidenceReconciliationRow> rows(@Param("org")Long org,@Param("kind")String kind,@Param("status")String status,@Param("packingId")Long packingId,@Param("code")String code,@Param("before")LocalDateTime before,@Param("offset")long offset);
    @Select("<script>SELECT COUNT(*) "+FROM+FILTER+"</script>")
    long count(@Param("org")Long org,@Param("kind")String kind,@Param("status")String status,@Param("packingId")Long packingId,@Param("code")String code,@Param("before")LocalDateTime before);
    @Select("SELECT "+COLUMNS+FROM+" WHERE r.kind=#{kind} AND r.record_id=#{id} AND (#{org} IS NULL OR f.organization_id=#{org})")
    EvidenceReconciliationRow find(@Param("kind")String kind,@Param("id")Long id,@Param("org")Long org);
}
