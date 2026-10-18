package in.voltforge.api.ai.conversation;

import in.voltforge.api.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.*;

@Service @RequiredArgsConstructor
public class ConversationStream {
    private final ConversationStore store;
    private final AiService ai;
    private final JsonMapper mapper;
    private static final Set<String> PUBLIC_METADATA=Set.of("confidence","citations","grounding","uncertainty","mode","model","fallbackUsed","fallbackReasonCode","engineeringAuthority","internetRetrieval","localRetrieval");
    private ServerSentEvent<String> event(String type,Object value) { return ServerSentEvent.<String>builder(mapper.writeValueAsString(Map.of("type",type,"data",value))).event(type).build(); }
    public Flux<ServerSentEvent<String>> stream(ConversationStore.Turn turn) {
        if(turn.replay()) return Flux.just(event("accepted",turn.user()),event("saved",turn.assistant()));
        var state=new State();
        return Flux.usingWhen(Mono.just(turn), ignored -> Flux.concat(
            Flux.just(event("accepted",turn.user())),
            ai.chatStream(turn.request()).timeout(Duration.ofSeconds(120)).takeUntilOther(Mono.delay(Duration.ofMinutes(3))).publishOn(Schedulers.boundedElastic())
                .concatMap(e -> Mono.fromCallable(() -> { synchronized(state) { return process(turn,state,e); } }))
                .concatMap(e -> state.terminal != null && !"saved".equals(e.event()) ? Flux.just(e,state.terminal) : Flux.just(e))
                .takeUntil(e -> "saved".equals(e.event()))
                .concatWith(Flux.defer(() -> state.finished ? Flux.empty() : Flux.just(finish(turn,state,"interrupted"))))
                .onErrorResume(error -> Flux.just(finish(turn,state,"failed")))),
            ignored -> cleanup(turn,state), (ignored,error)->cleanup(turn,state), ignored->cleanup(turn,state));
    }
    private Mono<Void> cleanup(ConversationStore.Turn turn,State state) {
        return Mono.fromRunnable(()->{ synchronized(state) { if(!state.finished) finish(turn,state,"interrupted"); } }).subscribeOn(Schedulers.boundedElastic()).then();
    }
    private ServerSentEvent<String> process(ConversationStore.Turn turn,State state,ServerSentEvent<String> event) {
        if(event.data()==null||event.data().equals("[DONE]")) return event;
        var json=mapper.readTree(event.data());
        var payload=json.has("payload")?json.get("payload"):json;
        String type=json.path("type").asString(event.event()==null?"":event.event());
        if(type.equals("token")||type.equals("delta")) state.content.append(payload.path("delta").asString(payload.path("content").asString("")));
        if(type.equals("complete")||type.equals("done")) {
            String reply=payload.path("reply").asString(payload.path("content").asString(""));
            if(!reply.isEmpty()) { state.content.setLength(0); state.content.append(reply); }
            if(state.content.length()>120000) return finish(turn,state,"failed");
            for(String key:PUBLIC_METADATA) if(payload.has(key)) state.metadata.put(key,mapper.convertValue(payload.get(key),Object.class));
            state.terminal=finish(turn,state,state.content.isEmpty()?"failed":"completed");
            return event;
        }
        if(type.equals("error")) {
            String code=payload.path("code").asString("AI_STREAM_FAILED");
            if(code.matches("[A-Z0-9_]{1,80}")) state.metadata.put("errorCode",code);
            state.terminal=finish(turn,state,"failed"); return event;
        }
        if(state.content.length()>120000) return finish(turn,state,"failed");
        if(System.nanoTime()-state.lastCheckpoint>1_000_000_000L) {
            var saved=store.checkpoint(turn,state.content.toString(),"pending",state.metadata);
            state.lastCheckpoint=System.nanoTime();
            if(!saved.status().equals("pending")) { state.finished=true; return event("saved",saved); }
        }
        return event;
    }
    private ServerSentEvent<String> finish(ConversationStore.Turn turn,State state,String status) {
        var saved=store.checkpoint(turn,state.content.toString(),status,state.metadata); state.finished=true; return event("saved",saved);
    }
    private static class State { final StringBuilder content=new StringBuilder(); final Map<String,Object> metadata=new HashMap<>(); long lastCheckpoint=System.nanoTime(); volatile boolean finished; ServerSentEvent<String> terminal; }
}
