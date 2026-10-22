package org.nongchanpin.mapper;

import java.util.List;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.AgentModels.*;

@Mapper
public interface AgentMapper {
    @Select("SELECT * FROM agent_report WHERE id=#{id}") Report report(Long id);
    @Select("SELECT owner_id FROM agent_report WHERE id=#{id}") Long owner(Long id);
    @Select("SELECT id FROM agent_report WHERE id=#{id} FOR UPDATE") Long lock(Long id);
    @Select("SELECT * FROM agent_report WHERE request_key=#{key}") Report byKey(String key);
    @Select("SELECT * FROM agent_report WHERE owner_id=#{owner} AND source_hash=#{hash}") Report bySource(@Param("owner")Long owner,@Param("hash")String hash);
    @Select("SELECT id,owner_id,report_date,LEFT(source_text,100) AS source_text,mode,status,version,created_at FROM agent_report WHERE owner_id=#{owner} ORDER BY id DESC LIMIT 20 OFFSET #{offset}") List<Report> list(@Param("owner")Long owner,@Param("offset")long offset);
    @Select("SELECT COUNT(*) FROM agent_report WHERE owner_id=#{owner}") long count(Long owner);
    @Insert("INSERT INTO agent_report (owner_id,report_date,source_text,source_hash,request_key,mode) VALUES (#{ownerId},#{reportDate},#{sourceText},#{sourceHash},#{requestKey},#{mode})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(Report report);
    @Insert("INSERT INTO agent_revision (report_id,version,plan_json,recorded_by) VALUES (#{report},#{version},#{plan},#{actor})") int revision(@Param("report")Long report,@Param("version")int version,@Param("plan")String plan,@Param("actor")Long actor);
    @Select("SELECT plan_json FROM agent_revision WHERE report_id=#{report} AND version=#{version}") String plan(@Param("report")Long report,@Param("version")int version);
    @Update("UPDATE agent_report SET version=version+1 WHERE id=#{id}") int advance(Long id);
    @Update("UPDATE agent_report SET status=#{status} WHERE id=#{id}") int status(@Param("id")Long id,@Param("status")String status);
    @Select("SELECT * FROM agent_confirmation WHERE request_key=#{key}") Confirmation confirmationByKey(String key);
    @Select("SELECT * FROM agent_confirmation WHERE id=#{id}") Confirmation confirmation(Long id);
    @Select("SELECT * FROM agent_confirmation WHERE report_id=#{id} ORDER BY id DESC LIMIT 100") List<Confirmation> confirmations(Long id);
    @Insert("INSERT INTO agent_confirmation (report_id,version,scope_json,request_key,request_hash,confirmed_by) VALUES (#{reportId},#{version},#{scopeJson},#{requestKey},#{requestHash},#{confirmedBy})")
    @Options(useGeneratedKeys=true,keyProperty="id") int confirm(Confirmation confirmation);
    @Update("UPDATE agent_confirmation SET status='COMPLETED' WHERE id=#{id}") int finish(Long id);
    @Select("SELECT r.* FROM agent_receipt r JOIN agent_confirmation c ON c.id=r.confirmation_id WHERE c.report_id=#{report} AND r.operation_id=#{operation} AND r.status='SUCCESS' ORDER BY r.id LIMIT 1") Receipt success(@Param("report")Long report,@Param("operation")String operation);
    @Select("SELECT * FROM agent_receipt WHERE confirmation_id=#{confirmation} AND operation_id=#{operation}") Receipt receipt(@Param("confirmation")Long confirmation,@Param("operation")String operation);
    @Select("SELECT COUNT(*) FROM agent_receipt WHERE confirmation_id=#{id}") int receiptCount(Long id);
    @Select("SELECT r.* FROM agent_receipt r JOIN agent_confirmation c ON c.id=r.confirmation_id WHERE c.report_id=#{id} ORDER BY r.id DESC LIMIT 200") List<Receipt> receipts(Long id);
    @Insert("INSERT INTO agent_receipt (confirmation_id,operation_id,status,business_id,message) VALUES (#{confirmationId},#{operationId},#{status},#{businessId},#{message})")
    @Options(useGeneratedKeys=true,keyProperty="id") int receiptInsert(Receipt receipt);
}
