package org.nongchanpin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.mapper.ChatMapper;
import org.nongchanpin.model.dto.ChatRequests;
import org.nongchanpin.model.entity.ChatModels.*;
import org.nongchanpin.security.*;
import org.springframework.transaction.*;

class ChatServiceTest {
    private final ChatMapper mapper=mock(ChatMapper.class);
    private final AgentPlanner model=mock(AgentPlanner.class);
    private final AgentReadTools reads=mock(AgentReadTools.class);
    private final AgentTools tools=mock(AgentTools.class);
    private final AgentService agent=mock(AgentService.class);
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private final ChatService chat=new ChatService(mapper,model,reads,tools,agent,json,new PayloadService(),manager);
    private final Map<Long,Turn> turns=new LinkedHashMap<>();
    private final Actor actor=new Actor();
    @BeforeEach void setup() throws Exception {
        actor.setId(1L);actor.setOrganizationId(3L);actor.setRole("MANAGER");AccessContext.set(actor);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(mapper.owner(7L)).thenReturn(1L);when(model.status()).thenReturn(Map.of("configured",true));when(tools.context("")).thenReturn(Map.of());
        when(mapper.byKey(any())).thenAnswer(c->turns.values().stream().filter(t->t.getRequestKey().equals(c.getArgument(0))).findFirst().orElse(null));
        when(mapper.turn(any())).thenAnswer(c->turns.get(c.getArgument(0)));
        when(mapper.latest(7L)).thenAnswer(c->{List<Turn> result=new ArrayList<>(turns.values());Collections.reverse(result);return result;});
        when(mapper.pending(7L)).thenAnswer(c->turns.values().stream().filter(t->t.getStatus().equals("PENDING")).findFirst().orElse(null));
        doAnswer(c->{Turn t=c.getArgument(0);t.setId((long)turns.size()+1);t.setStatus("PENDING");turns.put(t.getId(),t);return 1;}).when(mapper).insert(any());
        doAnswer(c->{Turn t=c.getArgument(0);t.setStatus("PENDING");return 1;}).when(mapper).claim(any());
        doAnswer(c->{Turn t=c.getArgument(0);t.setStatus("COMPLETE");return 1;}).when(mapper).complete(any());
        doAnswer(c->{Turn t=c.getArgument(0);t.setStatus("FAILED");return 1;}).when(mapper).fail(any());
        when(model.complete(anyString(),anyList())).thenReturn(response("你好，可以聊种植，也可以问日常问题。"));
    }
    @AfterEach void cleanup(){AccessContext.clear();}
    private JsonNode response(String reply)throws Exception{return json.readTree("{\"reply\":\""+reply+"\",\"readRequests\":[],\"operations\":[]}");}
    private ChatRequests.Message message(String text){ChatRequests.Message r=new ChatRequests.Message();r.setText(text);r.setRequestKey(UUID.randomUUID().toString());return r;}
    @Test void ordinaryConversationDoesNotCreateBusinessProposal(){Turn t=chat.send(7L,message("聊聊草莓种植吧"));assertEquals("COMPLETE",t.getStatus());assertNotNull(t.getReplyText());assertNull(t.getReportId());verifyNoInteractions(agent,reads);}
    @Test void nextMessageReceivesEarlierUserAndAssistantContext(){chat.send(7L,message("我的棚叫东棚"));chat.send(7L,message("刚才我说棚叫什么？"));verify(model,times(2)).complete(anyString(),anyList());verify(model).complete(anyString(),argThat(messages->messages.size()==3&&messages.get(0).get("content").equals("我的棚叫东棚")&&messages.get(1).get("role").equals("assistant")));}
    @Test void sameMessageRetryDoesNotCallModelOrAddMessageAgain(){ChatRequests.Message r=message("今天聊什么？");Long first=chat.send(7L,r).getId();assertEquals(first,chat.send(7L,r).getId());assertEquals(1,turns.size());verify(model,times(1)).complete(anyString(),anyList());r.setText("别的内容");assertThrows(IllegalArgumentException.class,()->chat.send(7L,r));}
    @Test void pendingMessageCannotBeSubmittedAgainOrInterleaved(){Turn pending=new Turn();pending.setId(1L);pending.setConversationId(7L);pending.setStatus("PENDING");pending.setLeaseUntil(LocalDateTime.now().plusMinutes(2));pending.setRequestKey(UUID.randomUUID().toString());turns.put(1L,pending);assertThrows(IllegalArgumentException.class,()->chat.send(7L,message("再发一条")));verifyNoInteractions(reads,agent);verify(model,never()).complete(anyString(),anyList());}
    @Test void failedReplyRetryReusesOriginalTurn(){when(model.complete(anyString(),anyList())).thenThrow(new IllegalArgumentException("模型不可用")).thenAnswer(c->response("现在可以回复"));ChatRequests.Message r=message("聊聊日常");assertThrows(IllegalArgumentException.class,()->chat.send(7L,r));assertEquals("FAILED",turns.get(1L).getStatus());assertEquals("COMPLETE",chat.send(7L,r).getStatus());assertEquals(1,turns.size());}
    @Test void otherUsersCannotReadOrSendIntoConversation(){actor.setId(2L);assertThrows(AccessException.class,()->chat.detail(7L,null));assertThrows(AccessException.class,()->chat.send(7L,message("越权请求")));verify(model,never()).complete(anyString(),anyList());}
    @Test void viewerCanChatButCannotGenerateProposals()throws Exception {actor.setRole("VIEWER");when(model.complete(anyString(),anyList())).thenReturn(json.readTree("{\"reply\":\"只读查询\",\"operations\":[{\"kind\":\"HARVEST\"}]}"));assertNull(chat.send(7L,message("帮我记录采收" )).getReportId());verifyNoInteractions(agent);}
    @Test void malformedOperationCannotSilentlyBecomeAnotherKindOfProposal()throws Exception{when(model.complete(anyString(),anyList())).thenReturn(json.readTree("{\"reply\":\"准备清单\",\"operations\":[{\"type\":\"HARVEST\",\"quote\":\"采收\"}]}"));Turn t=chat.send(7L,message("采收"));assertNull(t.getReportId());assertTrue(t.getReplyText().contains("尚未建立操作单"));verifyNoInteractions(agent);}
    @Test void readsAreRecordedAsEvidenceBeforeAnswer()throws Exception {when(model.complete(anyString(),anyList())).thenReturn(json.readTree("{\"reply\":\"正在查地块\",\"readRequests\":[{\"tool\":\"PLOTS\"}]}"),response("查到测试地块"));when(reads.read(any())).thenReturn(Map.of("label","地块台账","observedAt",LocalDateTime.now(),"link","/admin/production","data",List.of(Map.of("id",3,"name","测试地块"))));Turn t=chat.send(7L,message("有哪些地块？"));assertTrue(t.getEvidenceJson().contains("地块台账"));assertTrue(t.getEvidenceJson().contains("success"));verify(model).complete(anyString(),argThat(messages->messages.stream().anyMatch(m->m.get("content").contains("测试地块"))));}
    @Test void deniedReadIsNotClaimedAsSuccessfulEvidence()throws Exception {when(model.complete(anyString(),anyList())).thenReturn(json.readTree("{\"reply\":\"查仓库\",\"readRequests\":[{\"tool\":\"STOCK\",\"targetId\":9}]}"),response("无法查该仓库"));when(reads.read(any())).thenThrow(new AccessException(403,"拒绝"));Turn t=chat.send(7L,message("查别人的仓库"));assertTrue(t.getEvidenceJson().contains("\"success\":false"));assertNull(t.getReportId());}
    @Test void unfinishedReadLoopDoesNotInventFinalBusinessResult()throws Exception {when(model.complete(anyString(),anyList())).thenReturn(json.readTree("{\"reply\":\"查好了\",\"readRequests\":[{\"tool\":\"PLOTS\"}]}"));when(reads.read(any())).thenReturn(Map.of("label","地块","observedAt",LocalDateTime.now(),"link","/admin/production","data",List.of()));Turn t=chat.send(7L,message("继续查询"));assertTrue(t.getReplyText().contains("还没有查齐"));assertNull(t.getReportId());verify(model,times(3)).complete(anyString(),anyList());}
}
