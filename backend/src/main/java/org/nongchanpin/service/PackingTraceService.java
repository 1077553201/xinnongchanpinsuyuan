package org.nongchanpin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.*;
import org.nongchanpin.model.dto.IssuePackingCodesRequest;
import org.nongchanpin.model.entity.*;
import org.nongchanpin.model.entity.PackingTraceModels.*;
import org.nongchanpin.security.AccessContext;
import org.nongchanpin.util.HashUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class PackingTraceService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PackingTraceMapper traces;
    private final ProductBatchMapper batches;
    private final TraceItemMapper items;
    private final PayloadService payloads;
    private final AuditService audit;
    @Value("${public.traceBaseUrl:http://127.0.0.1:5173/t}") private String baseUrl;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> detail(Long id,int page) {
        Source source = access(id);
        if (page < 1) throw new IllegalArgumentException("页码无效。");
        long total = traces.count(id), current = Math.min((long)page,Math.max(1,(total+19)/20));
        Bridge bridge = traces.bridge(id);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("packing",source);
        result.put("source",bridge == null ? snapshot(source) : publicSource(bridge));
        result.put("generatedCount",total);
        result.put("remaining",source.getPackageCount()-total);
        result.put("rows",traces.items(id,(current-1)*20));
        result.put("page",current);
        result.put("total",total);
        result.put("unitRule","ONE_COMPLETE_PACKAGE");
        return result;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Map<String,Object> generate(Long id,IssuePackingCodesRequest request) {
        AccessContext.write();
        access(id);
        traces.lock(id);
        Source source = access(id);
        if (request.getCount() == null || request.getCount() < 1 || request.getCount() > 100) throw new IllegalArgumentException("每次赋码须为 1–100 的整数。");
        String hash = payloads.requestHash(Map.of("packing",id,"count",request.getCount(),"actor",AccessContext.require().getId(),"unitRule","ONE_COMPLETE_PACKAGE"));
        Issue previous = traces.byKey(request.getRequestKey());
        if (previous != null) {
            if (!hash.equals(previous.getRequestHash())) throw new IllegalArgumentException("操作标识已用于其他赋码内容，请刷新核对。");
            return result(previous);
        }
        long generated = traces.count(id);
        if (generated + request.getCount() > source.getPackageCount()) throw new IllegalArgumentException("赋码数量超过本批尚未赋码的完整包装件数。");
        Bridge bridge = traces.bridge(id);
        if (bridge == null) {
            String snapshot = payloads.canonicalJson(snapshot(source));
            bridge = new Bridge();bridge.setPackingId(id);bridge.setSourceJson(snapshot);bridge.setSourceHash(HashUtils.hashCanonicalTextPlainHex(snapshot));
            ProductBatchEntity batch = new ProductBatchEntity();
            batch.setProductId(source.getProductId());batch.setOrganizationId(source.getOrganizationId());
            batch.setBatchCode("PACK-"+UUID.randomUUID());batch.setProductionArea(source.getPlotName());
            batch.setPlantDate(source.getPlantedOn());batch.setHarvestDate(source.getHarvestedAt().toLocalDate());
            batch.setBatchMetadata(payloads.canonicalJson(Map.of("sourceType","PACKING_V1","packingId",id,"sourceHash",bridge.getSourceHash())));
            batch.setStatus("ACTIVE");batch.setChainStatus("NOT_SUBMITTED");
            batches.insert(batch);bridge.setBatchId(batch.getId());traces.insertBridge(bridge);
        }
        ProductBatchEntity batch = batches.findById(bridge.getBatchId());
        if (batch == null || !"ACTIVE".equals(batch.getStatus())) throw new IllegalArgumentException("关联批次状态不允许继续赋码，请先核对。");
        Issue issue = new Issue();issue.setPackingId(id);issue.setCount(request.getCount());issue.setRecordedBy(AccessContext.require().getId());issue.setRequestKey(request.getRequestKey());issue.setRequestHash(hash);
        traces.insertIssue(issue);
        for (int index=0;index<request.getCount();index++) {
            byte[] random = new byte[16];RANDOM.nextBytes(random);
            String code = "PK-"+Long.toString(id,36).toUpperCase(Locale.ROOT)+"-"+Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            TraceItemEntity item = new TraceItemEntity();item.setBatchId(bridge.getBatchId());item.setTraceCodePlain(code);
            item.setTraceCodeHash(HashUtils.hashBusinessIdPlainHex(code));item.setTraceCodeEncrypted(item.getTraceCodeHash());
            item.setTraceUrl(baseUrl.replaceAll("/+$","")+"/"+code);item.setQrStatus("GENERATED");item.setNfcStatus("UNBOUND");item.setStatus("ACTIVE");item.setChainStatus("NOT_SUBMITTED");
            items.insert(item);traces.link(item.getId(),id,issue.getId(),generated+index+1);
        }
        audit.record("PACKING_CODE_ISSUE","PACKING",id,"完整包装一码一件 / 本次 "+request.getCount()+" / 操作 #"+issue.getId());
        return result(issue);
    }

    private Source access(Long id) {
        Source source = traces.source(id);
        if (source == null) throw new IllegalArgumentException("包装来源不存在。");
        AccessContext.checkOrganization(source.getOrganizationId());
        return source;
    }
    private Map<String,Object> result(Issue issue) {
        return Map.of("issueId",issue.getId(),"packingId",issue.getPackingId(),"items",traces.issueItems(issue.getId()),"chainStatus","NOT_SUBMITTED");
    }
    private Map<String,Object> snapshot(Source source) {
        Map<String,Object> value = new LinkedHashMap<>();
        value.put("version",1);value.put("unitRule","ONE_COMPLETE_PACKAGE");
        value.put("packingId",source.getId());value.put("packingCode",source.getPackingCode());value.put("packageCount",source.getPackageCount());value.put("packageUnit",source.getPackageUnit());value.put("netWeightKg",source.getNetWeightKg());value.put("packedAt",source.getPackedAt());
        value.put("gradeId",source.getGradeId());value.put("gradeName",source.getGradeName());value.put("harvestId",source.getHarvestId());value.put("harvestCode",source.getHarvestCode());value.put("harvestedAt",source.getHarvestedAt());
        value.put("cycleId",source.getCycleId());value.put("cycleCode",source.getCycleCode());value.put("productName",source.getProductName());value.put("variety",source.getVariety());value.put("plantedOn",source.getPlantedOn());
        value.put("plotId",source.getPlotId());value.put("plotCode",source.getPlotCode());value.put("plotName",source.getPlotName());
        return value;
    }
    public static Map<String,Object> publicSource(Bridge bridge) {
        if (bridge == null) return null;
        try {
            if(!HashUtils.hashCanonicalTextPlainHex(bridge.getSourceJson()).equals(bridge.getSourceHash()))throw new IllegalArgumentException("来源快照摘要不一致。");
            Map<String,Object> value = new ObjectMapper().readValue(bridge.getSourceJson(),new TypeReference<Map<String,Object>>(){});
            Map<String,Object> safe = new LinkedHashMap<>();
            for (String key : List.of("version","unitRule","packingCode","packageUnit","netWeightKg","packedAt","gradeName","harvestCode","harvestedAt","cycleCode","productName","variety","plantedOn","plotName")) if(value.containsKey(key))safe.put(key,value.get(key));
            safe.put("sourceHash",bridge.getSourceHash());safe.put("verificationStatus","NOT_SUBMITTED");
            return safe;
        } catch(Exception e) { throw new IllegalArgumentException("来源快照无法读取，请联系管理人员核对。"); }
    }
}
