package org.nongchanpin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import org.nongchanpin.mapper.AgentMapper;
import org.nongchanpin.model.dto.AgentRequests;
import org.nongchanpin.model.entity.AgentModels.*;
import org.nongchanpin.security.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AgentService {
    private final AgentMapper mapper;
    private final AgentPlanner planner;
    private final AgentTools tools;
    private final PayloadService hashes;
    private final AuditService audit;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public AgentService(AgentMapper mapper,AgentPlanner planner,AgentTools tools,PayloadService hashes,AuditService audit,ObjectMapper json,PlatformTransactionManager manager) {
        this.mapper=mapper;this.planner=planner;this.tools=tools;this.hashes=hashes;this.audit=audit;this.json=json;
        tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Map<String,Object> status(){AccessContext.require();return planner.status();}
    public Map<String,Object> context(String search){return tools.context(search);}
    public Map<String,Object> list(int page){if(page<1)throw new IllegalArgumentException("页码不正确。");long owner=AccessContext.require().getId(),total=mapper.count(owner),current=Math.min((long)page,Math.max(1,(total+19)/20));return Map.of("rows",mapper.list(owner,(current-1)*20),"total",total,"page",current);}
    public Map<String,Object> detail(Long id){return tx.execute(s->{Report r=require(id,false);List<Operation> operations=plan(r.getId(),r.getVersion());List<Receipt> completed=new ArrayList<>();for(Operation op:operations){Receipt receipt=mapper.success(id,op.getId());if(receipt!=null)completed.add(receipt);}return Map.of("report",r,"operations",operations,"confirmations",mapper.confirmations(id),"receipts",mapper.receipts(id),"completed",completed);});}
    public Report create(AgentRequests.Create request) {
        AccessContext.write();if(request.getReportDate().isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))))throw new IllegalArgumentException("日报日期不能晚于今天。");
        String text=request.getSourceText().trim(),hash=hashes.requestHash(Map.of("actor",AccessContext.require().getId(),"date",request.getReportDate(),"text",text));
        Report existing=mapper.byKey(request.getRequestKey());if(existing!=null){checkOwner(existing.getId());same(hash,existing.getSourceHash());return existing;}
        existing=mapper.bySource(AccessContext.require().getId(),hash);if(existing!=null)return existing;
        List<Operation> operations=planner.plan(text,request.getReportDate(),tools.context(""));
        if(operations.isEmpty())throw new IllegalArgumentException("未找到可整理的内容。");
        Report report=new Report();report.setOwnerId(AccessContext.require().getId());report.setReportDate(request.getReportDate());report.setSourceText(text);report.setSourceHash(hash);report.setRequestKey(request.getRequestKey());report.setMode(planner.mode());
        try {return tx.execute(s->{for(Operation op:operations)tools.prepare(op);mapper.insert(report);mapper.revision(report.getId(),1,write(operations),AccessContext.require().getId());audit.record("AGENT_DRAFT","AGENT_REPORT",report.getId(),"版本1 / "+report.getMode());return mapper.report(report.getId());});}
        catch(DuplicateKeyException e){Report replay=mapper.byKey(request.getRequestKey());if(replay!=null){checkOwner(replay.getId());same(hash,replay.getSourceHash());return replay;}replay=mapper.bySource(AccessContext.require().getId(),hash);if(replay==null)throw e;return replay;}
    }
    public Report revise(Long id,AgentRequests.Revise request) {
        AccessContext.write();return tx.execute(s->{Report r=require(id,true);if(!"READY".equals(r.getStatus()))throw new IllegalArgumentException("正在执行，请先核对或恢复原确认。");if(!r.getVersion().equals(request.getVersion()))throw new IllegalArgumentException("草稿版本已变化，请刷新核对。");if(r.getVersion()>=100)throw new IllegalArgumentException("本日报已达100个版本，请联系管理员核对。");
            List<Operation> original=plan(id,r.getVersion());if(request.getOperations().size()!=original.size())throw new IllegalArgumentException("请保留原事项；不办理的事项在确认时排除。");
            Set<String> ids=new HashSet<>();
            for(Operation op:request.getOperations()) {
                Operation prior=original.stream().filter(x->x.getId().equals(op.getId())).findFirst().orElseThrow(()->new IllegalArgumentException("操作来源不一致。"));
                if(!ids.add(op.getId())||!Objects.equals(op.getQuote(),prior.getQuote()))throw new IllegalArgumentException("原文引用或事项标识不能覆盖。");
                if(mapper.success(id,op.getId())!=null && !actionHash(op).equals(actionHash(prior)))throw new IllegalArgumentException("成功事项不能改写或重做，请保留原业务事实。");
                tools.prepare(op);
            }
            mapper.revision(id,r.getVersion()+1,write(request.getOperations()),AccessContext.require().getId());mapper.advance(id);audit.record("AGENT_REVISE","AGENT_REPORT",id,"版本 "+(r.getVersion()+1));return mapper.report(id);
        });
    }
    public Map<String,Object> confirm(Long id,AgentRequests.Confirm request) {
        AccessContext.write();Confirmation confirmation=tx.execute(s->start(id,request));
        if("COMPLETED".equals(confirmation.getStatus()))return detail(id);
        for(String operation:scope(confirmation)) {
            try {tx.execute(s->{run(confirmation,operation);return null;});}
            catch(RuntimeException e){
                String message=e instanceof IllegalArgumentException?e.getMessage():e instanceof AccessException?"权限已变化，请重新核对来源。":"执行未完成，原业务与本次回执事务已回滚，请刷新核对后重试。";
                tx.execute(s->{require(id,true);if(mapper.receipt(confirmation.getId(),operation)==null)receipt(confirmation.getId(),operation,"FAILED",null,message);return null;});
            }
        }
        tx.execute(s->{require(id,true);if("RUNNING".equals(mapper.confirmation(confirmation.getId()).getStatus())&&mapper.receiptCount(confirmation.getId())==scope(confirmation).size()){mapper.finish(confirmation.getId());mapper.status(id,"READY");}return null;});
        return detail(id);
    }
    public Map<String,Object> resume(Long report,Long confirmation) {
        AccessContext.write();checkOwner(report);Confirmation c=mapper.confirmation(confirmation);
        if(c==null||!c.getReportId().equals(report))throw new IllegalArgumentException("确认记录不属于本日报。");
        AgentRequests.Confirm request=new AgentRequests.Confirm();request.setVersion(c.getVersion());request.setOperationIds(scope(c));request.setRequestKey(c.getRequestKey());return confirm(report,request);
    }
    private Confirmation start(Long id,AgentRequests.Confirm request) {
        Report r=require(id,true);String hash=hashes.requestHash(Map.of("report",id,"version",request.getVersion(),"scope",request.getOperationIds(),"actor",AccessContext.require().getId()));
        Confirmation previous=mapper.confirmationByKey(request.getRequestKey());if(previous!=null){same(hash,previous.getRequestHash());return previous;}
        if(!"READY".equals(r.getStatus()))throw new IllegalArgumentException("有未完成确认，请用原确认恢复，不能另起一轮执行。");
        if(!r.getVersion().equals(request.getVersion()))throw new IllegalArgumentException("确认版本已过期，请刷新后重新核对。");
        Set<String> selected=new HashSet<>(request.getOperationIds());if(selected.size()!=request.getOperationIds().size())throw new IllegalArgumentException("确认事项不能重复。");
        List<Operation> operations=plan(id,r.getVersion());
        for(String operation:selected) {
            Operation op=operations.stream().filter(x->x.getId().equals(operation)).findFirst().orElseThrow(()->new IllegalArgumentException("确认范围含未知事项。"));
            if("NOTE".equals(op.getKind())||!op.getIssues().isEmpty()||op.getContextHash()==null)throw new IllegalArgumentException("选中事项尚有缺项或只支持待办，请先补齐并保存新版本。");
            if(mapper.success(id,operation)!=null)throw new IllegalArgumentException("选中事项已经成功，不能再次确认落账。");
        }
        Confirmation c=new Confirmation();c.setReportId(id);c.setVersion(r.getVersion());c.setScopeJson(write(request.getOperationIds()));c.setRequestKey(request.getRequestKey());c.setRequestHash(hash);c.setConfirmedBy(AccessContext.require().getId());
        mapper.confirm(c);mapper.status(id,"RUNNING");audit.record("AGENT_CONFIRM","AGENT_REPORT",id,"版本 "+r.getVersion()+" / "+selected.size()+"项 / 确认 #"+c.getId());return mapper.confirmation(c.getId());
    }
    private void run(Confirmation c,String operation) {
        Report r=require(c.getReportId(),true);if(mapper.receipt(c.getId(),operation)!=null)return;
        if(!"RUNNING".equals(r.getStatus())||!r.getVersion().equals(c.getVersion()))throw new IllegalArgumentException("执行范围或版本已变化。");
        Operation op=plan(r.getId(),c.getVersion()).stream().filter(x->x.getId().equals(operation)).findFirst().orElseThrow(()->new IllegalArgumentException("操作不存在。"));
        Receipt previous=mapper.success(r.getId(),operation);if(previous!=null){receipt(c.getId(),operation,"SUCCESS",previous.getBusinessId(),"已成功，复用原业务记录。");return;}
        Long business=tools.execute(r.getId(),op);receipt(c.getId(),operation,"SUCCESS",business,"业务已保存；本工具不执行上链或设备指令。");audit.record("AGENT_RESULT","AGENT_REPORT",r.getId(),operation+" / 业务 #"+business);
    }
    private void receipt(Long confirmation,String operation,String status,Long business,String message){Receipt r=new Receipt();r.setConfirmationId(confirmation);r.setOperationId(operation);r.setStatus(status);r.setBusinessId(business);r.setMessage(message==null?"执行失败，请核对。":message.substring(0,Math.min(1000,message.length())));mapper.receiptInsert(r);}
    private Report require(Long id,boolean lock){checkOwner(id);if(lock)mapper.lock(id);return mapper.report(id);}
    private void checkOwner(Long id){Long owner=mapper.owner(id);if(owner==null)throw new IllegalArgumentException("日报不存在。");if(!owner.equals(AccessContext.require().getId()))throw new AccessException(403,"只能查看和办理自己提交的日报。");}
    private List<Operation> plan(Long id,int version){try{return json.readValue(mapper.plan(id,version),new TypeReference<List<Operation>>(){});}catch(Exception e){throw new IllegalStateException("草稿读取失败。");}}
    private List<String> scope(Confirmation c){try{return json.readValue(c.getScopeJson(),new TypeReference<List<String>>(){});}catch(Exception e){throw new IllegalStateException("确认范围读取失败。");}}
    private String actionHash(Operation op){return hashes.requestHash(Arrays.asList(op.getKind(),op.getTargetId(),op.getFields()));}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("草稿字段无法保存。");}}
    private void same(String a,String b){if(!a.equals(b))throw new IllegalArgumentException("确认标识已用于其他内容，请刷新核对。");}
}
