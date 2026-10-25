package org.nongchanpin.service;

import java.time.LocalDate;
import java.util.*;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.SourceQualityRequests.*;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.SourceQualityModels.*;
import org.nongchanpin.security.*;
import org.nongchanpin.util.HashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

@Service
@Validated
@RequiredArgsConstructor
public class SourceQualityService {
    private final SourceQualityMapper reports;
    private final TraceItemMapper items;
    private final PackingTraceMapper packing;
    private final PayloadService payloads;
    private final AuditService audit;
    private final QualityRevisionMapper revisions;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> list(String kind,Long id,int page){Source source=source(kind,id);AccessContext.checkOrganization(source.getOrganizationId());if(page<1)throw new IllegalArgumentException("页码须为正数。");long total=reports.count(kind,id),current=Math.min((long)page,Math.max(1,(total+19)/20));List<Report> rows=reports.list(kind,id,(current-1)*20);for(Report row:rows)validate(row);return Map.of("source",source,"rows",rows,"total",total,"page",current,"pageSize",20);}

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Report create(String kind,Long id,@Valid Create request,MultipartFile attachment){
        AccessContext.write();Source source=source(kind,id);AccessContext.checkOrganization(source.getOrganizationId());
        QualityFileSupport.FileData file=QualityFileSupport.read(attachment);
        LocalDate floor=("PACKING".equals(kind)?source.getPackedAt():source.getHarvestedAt()).toLocalDate();
        if(request.getSampledOn().isBefore(floor)||request.getCheckedOn().isBefore(request.getSampledOn())||request.getCheckedOn().isAfter(LocalDate.now()))throw new IllegalArgumentException("采样日期不能早于所属采收或包装，检测日期须在采样之后且不晚于今天。");
        reports.lockHarvest(source.getHarvestId());
        Report row=new Report();row.setOrganizationId(source.getOrganizationId());row.setScopeType(kind);row.setHarvestId(source.getHarvestId());row.setPackingId("PACKING".equals(kind)?source.getPackingId():null);row.setReportNo(request.getReportNo().trim());row.setAgencyName(request.getAgencyName().trim());row.setSampledOn(request.getSampledOn());row.setCheckedOn(request.getCheckedOn());row.setDeclaredResult(request.getDeclaredResult());row.setFileName(file.getName());row.setFileType(file.getType());row.setFileSize(file.getContent().length);row.setFileSha256(file.getHash());row.setManifestJson(payloads.canonicalJson(manifest(row,source)));row.setManifestHash(payloads.payloadHash(row.getManifestJson()));row.setRecordedBy(AccessContext.require().getId());row.setRecorderName(AccessContext.require().getDisplayName());row.setRequestKey(request.getRequestKey());row.setRequestHash(payloads.requestHash(Map.of("actor",row.getRecordedBy(),"manifestHash",row.getManifestHash(),"fileName",row.getFileName())));
        Report previous=reports.byRequest(request.getRequestKey());if(previous!=null){if(!row.getRequestHash().equals(previous.getRequestHash()))throw new IllegalArgumentException("确认标识已用于其他内容，请核对原登记记录。");validate(previous);return previous;}
        reports.insert(row);reports.attachment(row.getId(),file.getContent());audit.record("QUALITY_REGISTER","QUALITY_REPORT",row.getId(),kind+" #"+id+" / "+row.getManifestHash());return reports.report(row.getId());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Report publish(Long id,@Valid Publish request){
        AccessContext.write();Report observed=require(id);reports.lockHarvest(observed.getHarvestId());Report row=reports.lock(id);validate(row);
        if(!request.isMetadataReviewed()||!row.getManifestHash().equals(request.getExpectedManifestHash()))throw new IllegalArgumentException("请核对当前报告资料与摘要后再确认公开。");
        if(request.isPublishAttachment()&&!request.isAttachmentPrivacyReviewed())throw new IllegalArgumentException("公开附件前须单独确认已检查个人信息和其他敏感内容。");
        if("PUBLISHED".equals(row.getStatus())){if(row.isAttachmentPublic()!=request.isPublishAttachment())throw new IllegalArgumentException("原公开范围已确认，不能覆盖；需要更正时先撤回并保留原记录。");return row;}
        if(!"DRAFT".equals(row.getStatus()))throw new IllegalArgumentException("已撤回报告不能重新公开，请登记新的报告记录。");
        checkedContent(row);row.setAttachmentPublic(request.isPublishAttachment());row.setPublishedBy(AccessContext.require().getId());row.setPublisherName(AccessContext.require().getDisplayName());reports.publish(row);audit.record("QUALITY_PUBLISH","QUALITY_REPORT",id,"报告摘要 "+row.getManifestHash()+" / 附件公开 "+row.isAttachmentPublic());return reports.report(id);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Report withdraw(Long id,@Valid Withdraw request){
        AccessContext.write();Report observed=require(id);reports.lockHarvest(observed.getHarvestId());Report row=reports.lock(id);String reason=request.getReason().trim();
        if("WITHDRAWN".equals(row.getStatus())){if(!reason.equals(row.getWithdrawReason()))throw new IllegalArgumentException("报告已撤回，请查看原留档原因。");return row;}
        row.setWithdrawnBy(AccessContext.require().getId());row.setWithdrawReason(reason);reports.withdraw(row);audit.record("QUALITY_WITHDRAW","QUALITY_REPORT",id,reason);return reports.report(id);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public byte[] privateContent(Long id){Report row=require(id);validate(row);return checkedContent(row);}
    public Report privateReport(Long id){return require(id);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Report checkedReport(Long id){Report row=require(id);validate(row);return row;}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> publicFacts(Long reportId,Long itemId){
        PackingTraceModels.Bridge bridge=packing.publicBridge(itemId);Report row=reports.report(reportId);if(bridge==null||row==null||row.getPublishedAt()==null)throw unavailable();
        Source source=source("PACKING",bridge.getPackingId());boolean related="PACKING".equals(row.getScopeType())?bridge.getPackingId().equals(row.getPackingId()):source.getHarvestId().equals(row.getHarvestId());if(!related||!source.getOrganizationId().equals(row.getOrganizationId()))throw unavailable();validate(row);
        Map<String,Object> value=payloads.map();value.put("reportId",reportId);value.put("report",payloads.readJson(row.getManifestJson()));value.put("reportManifestHash",row.getManifestHash());value.put("status",row.getStatus());value.put("attachmentPublic","PUBLISHED".equals(row.getStatus())&&row.isAttachmentPublic());value.put("publishedAt",row.getPublishedAt());value.put("withdrawnAt",row.getWithdrawnAt());value.put("revisionLinks",publicLinks(reportId));return value;
    }
    private List<Map<String,Object>> publicLinks(Long id){List<Map<String,Object>> values=new ArrayList<>();for(org.nongchanpin.model.entity.QualityEvidenceModels.Revision link:revisions.publicLinks(id))values.add(Map.of("previousReportId",link.getPreviousReportId(),"replacementReportId",link.getReplacementReportId(),"previousReportNo",link.getPreviousReportNo(),"replacementReportNo",link.getReplacementReportNo(),"previousHash",link.getPreviousHash(),"replacementHash",link.getReplacementHash()));return values;}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> publicReports(Long packingId,String traceCode){
        Source source=source("PACKING",packingId);List<Map<String,Object>> rows=new ArrayList<>();
        for(Report row:reports.publicReports(packingId,source.getHarvestId())){validate(row);Map<String,Object> value=new LinkedHashMap<>();value.put("id",row.getId());value.put("scopeType",row.getScopeType());value.put("reportNo",row.getReportNo());value.put("agencyName",row.getAgencyName());value.put("sampledOn",row.getSampledOn());value.put("checkedOn",row.getCheckedOn());value.put("declaredResult",row.getDeclaredResult());value.put("status",row.getStatus());value.put("publishedAt",row.getPublishedAt());value.put("withdrawnAt",row.getWithdrawnAt());value.put("fileSha256",row.getFileSha256());value.put("manifestHash",row.getManifestHash());value.put("fileSize",row.getFileSize());value.put("fileType",row.getFileType());value.put("chainStatus","NOT_SUBMITTED");value.put("revisionLinks",publicLinks(row.getId()));if("PUBLISHED".equals(row.getStatus())&&row.isAttachmentPublic())value.put("downloadPath","/public/trace/"+org.springframework.web.util.UriUtils.encodePathSegment(traceCode,java.nio.charset.StandardCharsets.UTF_8)+"/quality-reports/"+row.getId()+"/attachment");rows.add(value);}
        return Map.of("rows",rows,"total",reports.publicCount(packingId,source.getHarvestId()),"limit",50);
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> publicContent(String code,Long id){
        TraceItemEntity item=items.findByTraceCodeHash(HashUtils.hashBusinessIdPlainHex(code));PackingTraceModels.Bridge bridge=item==null?null:packing.publicBridge(item.getId());Report row=reports.report(id);
        if(bridge==null||row==null||!"PUBLISHED".equals(row.getStatus())||!row.isAttachmentPublic())throw unavailable();
        Source source=source("PACKING",bridge.getPackingId());boolean related="PACKING".equals(row.getScopeType())?bridge.getPackingId().equals(row.getPackingId()):source.getHarvestId().equals(row.getHarvestId());
        if(!related||!source.getOrganizationId().equals(row.getOrganizationId()))throw unavailable();validate(row);
        return Map.of("content",checkedContent(row),"fileType",row.getFileType(),"fileName","quality-report-"+id+extension(row.getFileType()));
    }

    private AccessException unavailable(){return new AccessException(404,"该公开附件不可用。");}
    private Report require(Long id){Report row=reports.report(id);if(row==null)throw new IllegalArgumentException("质检报告不存在。");AccessContext.checkOrganization(row.getOrganizationId());return row;}
    private Source source(String kind,Long id){Source source="HARVEST".equals(kind)?reports.harvest(id):"PACKING".equals(kind)?reports.packing(id):null;if(source==null)throw new IllegalArgumentException("请选择有效的采收或包装来源。");return source;}
    private byte[] checkedContent(Report row){Attachment attachment=reports.content(row.getId());byte[] content=attachment==null?null:attachment.getContent();if(content==null||content.length!=row.getFileSize()||!QualityFileSupport.digest(content).equals(row.getFileSha256()))throw new IllegalArgumentException("附件缺失或内容与登记摘要不一致，已停止下载或公开。");return content;}
    private void validate(Report row){Source source=source(row.getScopeType(),"PACKING".equals(row.getScopeType())?row.getPackingId():row.getHarvestId());if(!source.getOrganizationId().equals(row.getOrganizationId())||!source.getHarvestId().equals(row.getHarvestId())||!payloads.payloadHash(row.getManifestJson()).equals(row.getManifestHash())||!payloads.canonicalJson(manifest(row,source)).equals(row.getManifestJson()))throw new IllegalArgumentException("报告来源或已登记摘要不一致，请核对原始记录。");}
    private Map<String,Object> manifest(Report row,Source source){Map<String,Object> value=payloads.map();value.put("version",1);value.put("scopeType",row.getScopeType());value.put("harvestId",row.getHarvestId());value.put("packingId",row.getPackingId());value.put("harvestCode",source.getHarvestCode());value.put("packingCode",source.getPackingCode());value.put("cycleCode",source.getCycleCode());value.put("reportNo",row.getReportNo());value.put("agencyName",row.getAgencyName());value.put("sampledOn",row.getSampledOn());value.put("checkedOn",row.getCheckedOn());value.put("declaredResult",row.getDeclaredResult());value.put("fileSha256",row.getFileSha256());value.put("fileType",row.getFileType());value.put("fileSize",row.getFileSize());return value;}
    public static String extension(String type){return "application/pdf".equals(type)?".pdf":"image/png".equals(type)?".png":".jpg";}
}
