package in.voltforge.api.ai.conversation;

import in.voltforge.api.common.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController @RequestMapping("/api/v1/ai/conversations") @RequiredArgsConstructor
public class ConversationController {
    private final ConversationStore store;
    private final ConversationStream streaming;
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConversationStore.Summary> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ConversationRequests.Create request) {
        return ApiResponse.success("Conversation created",store.create(jwt.getSubject(),request.projectId()));
    }
    private <T> PagedResponse<T> page(Page<T> p) { return PagedResponse.<T>builder().content(p.getContent()).page(p.getNumber()).size(p.getSize()).totalElements(p.getTotalElements()).totalPages(p.getTotalPages()).first(p.isFirst()).last(p.isLast()).build(); }
    @GetMapping public ApiResponse<PagedResponse<ConversationStore.Summary>> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required=false) String projectId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="30") int size) {
        return ApiResponse.success("Conversations loaded",page(store.list(jwt.getSubject(),projectId,q,page,size)));
    }
    @GetMapping("/{id}") public ApiResponse<ConversationStore.Summary> get(@AuthenticationPrincipal Jwt jwt,@PathVariable String id) { return ApiResponse.success("Conversation loaded",store.get(jwt.getSubject(),id)); }
    @GetMapping("/{id}/messages") public ApiResponse<PagedResponse<ConversationStore.Message>> messages(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size) {
        return ApiResponse.success("Messages loaded",page(store.history(jwt.getSubject(),id,page,size)));
    }
    @PatchMapping("/{id}") public ApiResponse<ConversationStore.Summary> rename(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody ConversationRequests.Rename request) { return ApiResponse.success("Conversation renamed",store.rename(jwt.getSubject(),id,request.title())); }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable String id) { store.delete(jwt.getSubject(),id); return ApiResponse.success("Conversation deleted",null); }
    @PostMapping("/{id}/stop") public ApiResponse<Void> stop(@AuthenticationPrincipal Jwt jwt,@PathVariable String id) { store.stop(jwt.getSubject(),id); return ApiResponse.success("Generation stopped",null); }
    @PostMapping(value="/{id}/messages/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> send(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody ConversationRequests.Send request) {
        return streaming.stream(store.begin(jwt.getSubject(),id,request));
    }
}
