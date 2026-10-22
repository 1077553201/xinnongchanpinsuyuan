package org.nongchanpin.service;

import java.time.LocalDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.mapper.ChatMapper;
import org.nongchanpin.security.AccessContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentReadTools {
    private final ChatMapper chats;
    private final InventoryService inventory;
    private final SalesService sales;
    private final TransportService transport;
    private final HarvestService harvests;
    private final ObjectMapper json;
    public static final Map<String,String> LABELS=Map.of("PLOTS","地块台账","CYCLES","种植周期","WAREHOUSES","仓库清单","STOCK","仓库现货","SALES","销售订单","TRANSPORT","运输进度","FARMING","农事待办","ENVIRONMENT","环境异常","HARVESTS","采收记录");

    public Map<String,Object> read(JsonNode input) {
        AccessContext.require();String kind=input.path("tool").asText(),search=input.path("search").asText("").trim();
        if(!LABELS.containsKey(kind)||search.length()>128)throw new IllegalArgumentException("查询类型或关键词无效。");
        Long target=input.path("targetId").isIntegralNumber()?input.get("targetId").longValue():null;Object rows;String link;
        switch(kind) {
            case "PLOTS":rows=target==null?chats.plots(AccessContext.organization(),search):chats.plotById(AccessContext.organization(),target);link="/admin/production";break;
            case "CYCLES":rows=target==null?chats.cycles(AccessContext.organization(),search):chats.cycleById(AccessContext.organization(),target);link="/admin/production";break;
            case "WAREHOUSES":rows=chats.warehouses(AccessContext.organization(),search);link="/admin/inventory";break;
            case "STOCK":
                positive(target);Map<String,Object> stock=inventory.warehouseStock(target,search,false,1,10);
                rows=Map.of("warehouse",stock.get("warehouse"),"rows",stock.get("rows"),"total",stock.get("total"),"units",stock.get("units"));link="/admin/inventory/warehouse/"+target;break;
            case "SALES":rows=projectPage(sales.list(search,input.path("status").asText(""),1,10),Set.of("id","orderCode","orderedOn","customerName","totalAmount","status"));link="/admin/sales";break;
            case "TRANSPORT":rows=projectPage(transport.list(search,input.path("status").asText(""),1,10),Set.of("id","shipmentCode","orderCode","productName","variety","packageCount","packageUnit","status","recordedAt"));link="/admin/transport";break;
            case "FARMING":rows=chats.tasks(AccessContext.organization(),search);link="/admin/farming";break;
            case "ENVIRONMENT":rows=chats.cases(AccessContext.organization(),search);link="/admin/farming";break;
            case "HARVESTS":positive(target);rows=harvests.list(target).stream().limit(10).collect(java.util.stream.Collectors.toList());link="/admin/harvest?cycle="+target;break;
            default:throw new IllegalArgumentException("不支持的查询。");
        }
        Map<String,Object> result=new LinkedHashMap<>();result.put("tool",kind);result.put("label",LABELS.get(kind));result.put("observedAt",LocalDateTime.now());result.put("link",link);result.put("data",rows);result.put("limit",List.of("STOCK","SALES","TRANSPORT","HARVESTS").contains(kind)?10:20);return result;
    }
    private Map<String,Object> projectPage(Map<String,Object> page,Set<String> fields) {
        List<Map<String,Object>> rows=new ArrayList<>();
        for(JsonNode row:json.valueToTree(page.get("rows"))){Map<String,Object> view=new LinkedHashMap<>();for(String field:fields)if(row.has(field))view.put(field,json.convertValue(row.get(field),Object.class));rows.add(view);}
        return Map.of("rows",rows,"total",page.get("total"));
    }
    private void positive(Long id){if(id==null||id<1)throw new IllegalArgumentException("请先查清并指定仓库或种植周期。");}
}
