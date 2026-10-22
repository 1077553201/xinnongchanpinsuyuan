package org.nongchanpin.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;
import org.nongchanpin.model.entity.ChatModels.*;

@Mapper
public interface ChatMapper {
    @Select("SELECT owner_id FROM agent_conversation WHERE id=#{id}") Long owner(Long id);
    @Select("SELECT id FROM agent_conversation WHERE id=#{id} FOR UPDATE") Long lock(Long id);
    @Select("SELECT * FROM agent_conversation WHERE id=#{id}") Conversation conversation(Long id);
    @Select("SELECT COUNT(*) FROM agent_conversation WHERE owner_id=#{owner}") long count(Long owner);
    @Select("SELECT * FROM agent_conversation WHERE owner_id=#{owner} ORDER BY updated_at DESC,id DESC LIMIT 20 OFFSET #{offset}") List<Conversation> list(@Param("owner")Long owner,@Param("offset")long offset);
    @Insert("INSERT INTO agent_conversation(owner_id,title) VALUES(#{ownerId},#{title})") @Options(useGeneratedKeys=true,keyProperty="id") int create(Conversation conversation);
    @Update("UPDATE agent_conversation SET title=IF(title='新对话',#{title},title),updated_at=CURRENT_TIMESTAMP(6) WHERE id=#{id}") int touch(@Param("id")Long id,@Param("title")String title);
    @Select("SELECT * FROM agent_chat_turn WHERE conversation_id=#{id} ORDER BY id DESC LIMIT 30") List<Turn> latest(Long id);
    @Select("SELECT COUNT(*) FROM agent_chat_turn WHERE conversation_id=#{id}") long turnCount(Long id);
    @Select("SELECT * FROM agent_chat_turn WHERE conversation_id=#{id} AND (#{before} IS NULL OR id<#{before}) ORDER BY id DESC LIMIT 30") List<Turn> page(@Param("id")Long id,@Param("before")Long before);
    @Select("SELECT * FROM agent_chat_turn WHERE conversation_id=#{id} AND status='PENDING' ORDER BY id DESC LIMIT 1") Turn pending(Long id);
    @Select("SELECT * FROM agent_chat_turn WHERE request_key=#{key}") Turn byKey(String key);
    @Select("SELECT * FROM agent_chat_turn WHERE id=#{id}") Turn turn(Long id);
    @Select("SELECT t.report_id FROM agent_chat_turn t JOIN agent_report r ON r.id=t.report_id WHERE t.conversation_id=#{id} AND t.proposal_hash=#{hash} AND r.status<>'SUPERSEDED' ORDER BY t.id LIMIT 1") Long previousProposal(@Param("id")Long id,@Param("hash")String hash);
    @Select("SELECT COUNT(*) FROM agent_chat_turn WHERE conversation_id=#{id} AND report_id=#{report}") int hasProposal(@Param("id")Long id,@Param("report")Long report);
    @Insert("INSERT INTO agent_chat_turn(conversation_id,request_key,request_hash,user_text,attempt_token,lease_until) VALUES(#{conversationId},#{requestKey},#{requestHash},#{userText},#{attemptToken},#{leaseUntil})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(Turn turn);
    @Update("UPDATE agent_chat_turn SET status='PENDING',attempt_token=#{attemptToken},lease_until=#{leaseUntil},error_message=NULL WHERE id=#{id}") int claim(Turn turn);
    @Update("UPDATE agent_chat_turn SET reply_text=#{replyText},report_id=#{reportId},proposal_hash=#{proposalHash},evidence_json=#{evidenceJson},status='COMPLETE',finished_at=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND attempt_token=#{attemptToken}") int complete(Turn turn);
    @Update("UPDATE agent_chat_turn SET status='FAILED',error_message=#{errorMessage},finished_at=CURRENT_TIMESTAMP(6) WHERE id=#{id} AND attempt_token=#{attemptToken}") int fail(Turn turn);

    String SCOPE=" (#{org} IS NULL OR p.organization_id=#{org}) ";
    @Select("SELECT c.id,p.plot_name,c.cycle_code,c.product_name,c.variety,c.status,c.planted_on FROM planting_cycle c JOIN farm_plot p ON p.id=c.plot_id WHERE "+SCOPE+" AND c.id=#{id} LIMIT 1") List<Map<String,Object>> cycleById(@Param("org")Long org,@Param("id")Long id);
    @Select("SELECT p.id,p.plot_code,p.plot_name,p.area_mu,p.manager_name FROM farm_plot p WHERE "+SCOPE+" AND p.id=#{id} LIMIT 1") List<Map<String,Object>> plotById(@Param("org")Long org,@Param("id")Long id);
    @Select("SELECT p.id,p.plot_code,p.plot_name,p.area_mu,p.manager_name FROM farm_plot p WHERE "+SCOPE+" AND (#{search}='' OR LOCATE(#{search},CONCAT_WS(' ',p.plot_code,p.plot_name,p.manager_name))>0) ORDER BY p.id DESC LIMIT 20") List<Map<String,Object>> plots(@Param("org")Long org,@Param("search")String search);
    @Select("SELECT c.id,p.plot_name,c.cycle_code,c.product_name,c.variety,c.status,c.planted_on,c.expected_harvest_on FROM planting_cycle c JOIN farm_plot p ON p.id=c.plot_id WHERE "+SCOPE+" AND (#{search}='' OR LOCATE(#{search},CONCAT_WS(' ',p.plot_name,c.cycle_code,c.product_name,c.variety))>0) ORDER BY c.id DESC LIMIT 20") List<Map<String,Object>> cycles(@Param("org")Long org,@Param("search")String search);
    @Select("SELECT id,warehouse_code,warehouse_name FROM inventory_warehouse WHERE (#{org} IS NULL OR organization_id=#{org}) AND (#{search}='' OR LOCATE(#{search},CONCAT_WS(' ',warehouse_code,warehouse_name))>0) ORDER BY id DESC LIMIT 20") List<Map<String,Object>> warehouses(@Param("org")Long org,@Param("search")String search);
    @Select("SELECT t.id,t.cycle_id,p.plot_name,t.title,t.planned_on,t.assignee_name,t.status FROM farm_task t JOIN planting_cycle c ON c.id=t.cycle_id JOIN farm_plot p ON p.id=c.plot_id WHERE "+SCOPE+" AND (#{search}='' OR LOCATE(#{search},CONCAT_WS(' ',p.plot_name,c.cycle_code,t.title,t.status))>0) ORDER BY (t.status='PLANNED') DESC,t.planned_on,t.id DESC LIMIT 20") List<Map<String,Object>> tasks(@Param("org")Long org,@Param("search")String search);
    @Select("SELECT e.id,e.cycle_id,p.plot_name,e.status,e.action_taken,e.handled_at,r.metric,r.value,r.unit,r.reported_at,r.device_code FROM environment_case e JOIN planting_cycle c ON c.id=e.cycle_id JOIN farm_plot p ON p.id=c.plot_id JOIN iot_sensor_reading r ON r.id=e.reading_id WHERE "+SCOPE+" AND (#{search}='' OR LOCATE(#{search},CONCAT_WS(' ',p.plot_name,c.cycle_code,e.status,r.metric))>0) ORDER BY (e.status='OPEN') DESC,e.id DESC LIMIT 20") List<Map<String,Object>> cases(@Param("org")Long org,@Param("search")String search);
}
