package org.nongchanpin.service;

import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.PackingEvidenceMapper;
import org.nongchanpin.model.entity.PackingTraceModels.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PackingManifestService {
    private final PackingEvidenceMapper evidence;
    public Map<String,Object> build(Source source,Bridge bridge) {
        Map<String,Object> origin = PackingTraceService.publicSource(bridge);
        LocalDateTime from = LocalDate.parse(origin.get("plantedOn").toString()).atStartOfDay();
        LocalDateTime until = LocalDateTime.parse(origin.get("harvestedAt").toString());
        if (until.isBefore(from)) throw new IllegalArgumentException("来源时间范围不正确，请核对原包装。");
        Map<String,Object> manifest = new LinkedHashMap<>();
        origin.remove("verificationStatus");
        manifest.put("version",1);manifest.put("source",origin);
        manifest.put("from",from);manifest.put("until",until);manifest.put("timezone","Asia/Shanghai");
        manifest.put("rules",section(evidence.rules(source.getCycleId(),from,until),200));
        manifest.put("farming",section(evidence.farming(source.getCycleId(),from,until),200));
        manifest.put("environment",section(evidence.environment(source.getCycleId(),from,until),50));
        manifest.put("coverage","仅包括本次冻结时已记录、属于原周期且在种植至采收时间范围内的资料；环境窗口须完整落在范围内，未返回或缺测不等于环境合格。农事为登记事实，不代表独立现场核实。");
        manifest.put("circulationStatus","NOT_LINKED_TO_ITEM");
        return manifest;
    }
    private Map<String,Object> section(List<Map<String,Object>> records,int limit) {
        List<Map<String,Object>> safe = new ArrayList<>();
        for (Map<String,Object> record : records.subList(0,Math.min(records.size(),limit))) {
            Map<String,Object> normalized = new LinkedHashMap<>();
            record.forEach((key,value)->normalized.put(key,value instanceof java.sql.Timestamp?((java.sql.Timestamp)value).toLocalDateTime().toString():value));
            safe.add(normalized);
        }
        return Map.of("rows",safe,"limit",limit,"truncated",records.size()>limit);
    }
}
