package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AgentPlannerHttpTest {
    @Test void compatibleAdapterSendsScopedContextAndParsesStructuredSuggestions() throws Exception {
        ObjectMapper json=new ObjectMapper();HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        List<String> requests=new ArrayList<>();
        server.createContext("/v1/chat/completions",exchange->{
            try {
                requests.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                requests.add(exchange.getRequestHeaders().getFirst("Authorization"));
                String content=json.writeValueAsString(Map.of("operations",List.of(Map.of("kind","NOTE","quote","明天计划送货。","fields",Map.of()))));
                byte[] result=json.writeValueAsBytes(Map.of("choices",List.of(Map.of("message",Map.of("content",content)))));
                exchange.sendResponseHeaders(200,result.length);exchange.getResponseBody().write(result);
            } finally {exchange.close();}
        });server.start();
        try {
            AgentPlanner planner=new AgentPlanner(json);configure(planner,server.getAddress().getPort());
            assertEquals("MODEL",planner.mode());assertEquals(true,planner.status().get("configured"));
            assertEquals("NOTE",planner.plan("明天计划送货。",LocalDate.of(2026,10,22),Map.of("cycles",List.of(Map.of("id",3,"label","本机构测试周期")))).get(0).getKind());
            assertEquals("qa-only-model",json.readTree(requests.get(0)).path("model").asText());
            assertEquals("Bearer qa-only-key",requests.get(1));assertTrue(requests.get(0).contains("本机构测试周期"));assertTrue(requests.get(0).contains("2026-10-22"));
        } finally {server.stop(0);}
    }
    @Test void providerFailureDoesNotExposeResponseOrPretendLocalFallback() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange->{byte[] response="PRIVATE_PROVIDER_DIAGNOSTIC".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(401,response.length);exchange.getResponseBody().write(response);exchange.close();});server.start();
        try {AgentPlanner planner=new AgentPlanner(new ObjectMapper());configure(planner,server.getAddress().getPort());IllegalArgumentException e=assertThrows(IllegalArgumentException.class,()->planner.plan("日报",LocalDate.now(),Map.of()));assertFalse(e.getMessage().contains("PRIVATE"));assertTrue(e.getMessage().contains("尚未创建草稿"));assertEquals("MODEL",planner.mode());}finally{server.stop(0);}
    }
    private void configure(AgentPlanner planner,int port){ReflectionTestUtils.setField(planner,"provider","compatible");ReflectionTestUtils.setField(planner,"baseUrl","http://127.0.0.1:"+port+"/v1");ReflectionTestUtils.setField(planner,"apiKey","qa-only-key");ReflectionTestUtils.setField(planner,"model","qa-only-model");}
}
