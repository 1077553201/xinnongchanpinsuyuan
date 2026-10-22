package org.nongchanpin.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import org.nongchanpin.mapper.ChatMapper;
import org.nongchanpin.model.entity.ChatModels.Turn;

/** Public execution events and scoped results, never model reasoning or credentials. */
final class ChatRun {
    final List<Map<String,Object>> events = new ArrayList<>();
    private final ChatMapper mapper;
    private final ObjectMapper json;
    private final Turn turn;

    ChatRun(ChatMapper mapper, ObjectMapper json, Turn turn) {
        this.mapper = mapper;
        this.json = json;
        this.turn = turn;
    }

    Map<String,Object> start(String kind, String label) {
        Map<String,Object> event = new LinkedHashMap<>();
        event.put("kind", kind);
        event.put("label", label);
        event.put("state", "RUNNING");
        event.put("observedAt", LocalDateTime.now());
        events.add(event);
        save();
        return event;
    }

    void finish(Map<String,Object> event, boolean success, String note) {
        event.put("state", success ? "DONE" : "FAILED");
        event.put("success", success);
        event.put("note", note);
        event.put("finishedAt", LocalDateTime.now());
        event.put("durationMs", Duration.between((LocalDateTime) event.get("observedAt"), LocalDateTime.now()).toMillis());
        save();
    }

    void save() {
        try { turn.setEvidenceJson(json.writeValueAsString(events)); }
        catch (Exception e) { throw new IllegalArgumentException("执行进度无法保存。"); }
        if (mapper.progress(turn) != 1) throw new IllegalArgumentException("本次回复已过期，请刷新查看最新进度。");
    }

    static ObjectNode query(ObjectMapper json, JsonNode request) {
        ObjectNode query = json.createObjectNode();
        query.put("tool", request.path("tool").asText().toUpperCase(Locale.ROOT));
        query.put("search", request.path("search").asText("").trim());
        query.put("status", Set.of("CYCLES", "SALES", "TRANSPORT").contains(query.path("tool").asText()) ? request.path("status").asText("").trim().toUpperCase(Locale.ROOT) : "");
        if (request.path("targetId").isIntegralNumber()) query.set("targetId", request.get("targetId"));
        if (Set.of("PLOTS", "CYCLES").contains(query.path("tool").asText()) && query.has("targetId")) query.put("search", "");
        return query;
    }

    static int count(JsonNode data) {
        return data.isArray() ? data.size() : data.path("rows").isArray() ? data.path("rows").size() : 0;
    }

    static String summary(JsonNode result) {
        JsonNode data = result.path("data");
        int count = count(data);
        String scope = data.path("warehouse").path("warehouseName").asText("");
        if (scope.isEmpty()) scope = data.path("warehouse").path("warehouse_name").asText("");
        String total = data.has("total") ? "，符合条件共 " + data.path("total").asLong() + " 条" : "，本次最多返回 " + result.path("limit").asInt(20) + " 条";
        return (scope.isEmpty() ? "" : scope + "：") + "返回 " + count + " 条" + total;
    }

    String fallback() {
        StringBuilder reply = new StringBuilder("本次已取得的查询结果如下，可展开下方记录查看明细：");
        for (Map<String,Object> event : events) {
            if (!"READ".equals(event.get("kind"))) continue;
            reply.append("\n• ").append(event.get("label")).append("：").append(event.getOrDefault("note", "查询未完成"));
            JsonNode data = json.valueToTree(event.get("data"));
            JsonNode rows = data.isArray() ? data : data.path("rows");
            if (rows.isArray()) {
                for (int index = 0; index < Math.min(rows.size(), 5); index++) reply.append("\n  ").append(index + 1).append(". ").append(record(rows.get(index)));
                if (rows.size() > 5) reply.append("\n  其余 ").append(rows.size() - 5).append(" 条请展开记录查看。");
            }
        }
        reply.append("\n以上仅是列出的查询范围，未完成的查询不代表没有记录；本次没有执行业务。");
        return reply.toString();
    }

    private String record(JsonNode row) {
        List<String> values = new ArrayList<>();
        for (String[] keys : new String[][]{{"warehouse_name","warehouseName"},{"plot_name","plotName"},{"product_name","productName"},{"variety"},{"title"}}) {
            for (String key : keys) if (row.hasNonNull(key)) { values.add(row.get(key).asText()); break; }
        }
        if (row.hasNonNull("availableCount")) values.add("在库 " + row.get("availableCount").asText() + row.path("packageUnit").asText("件"));
        if (row.hasNonNull("availableWeightKg")) values.add("净重 " + row.get("availableWeightKg").asText() + " kg");
        if (row.hasNonNull("id")) values.add("编号 " + row.get("id").asText());
        return values.isEmpty() ? "已返回明细，请在下方展开核对" : String.join(" · ", values);
    }
}
