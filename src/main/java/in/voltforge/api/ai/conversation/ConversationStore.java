package in.voltforge.api.ai.conversation;

import in.voltforge.api.ai.dto.AiChatRequest;
import in.voltforge.api.ai.gateway.AiGatewayPolicy;
import in.voltforge.api.common.exception.*;
import in.voltforge.api.project.repository.ProjectRepository;
import in.voltforge.api.project.service.ProjectService;
import in.voltforge.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** All mutations serialize on the conversation row; provider calls never hold a DB lock. */
@Service @RequiredArgsConstructor
public class ConversationStore {
    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final AiGatewayPolicy policy;

    public record Summary(String id, String projectId, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
        static Summary of(Conversation c) { return new Summary(c.getId(), c.getProjectId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt()); }
    }
    public record Message(String id, String conversationId, String clientRequestId, long sequenceNumber,
                          String role, String content, String status, LocalDateTime createdAt, Map<String,Object> metadata) {
        static Message of(ConversationMessage m) { return new Message(m.getId(),m.getConversationId(),m.getClientRequestId(),m.getSequenceNumber(),m.getRole(),m.getContent(),m.getStatus(),m.getCreatedAt(),m.getMetadata()); }
    }
    public record Turn(Summary conversation, Message user, Message assistant, String attemptId, AiChatRequest request, boolean replay) {}

    private String owner(String subject) {
        return users.findByKeycloakId(subject).orElseThrow(() -> new ForbiddenException("Sign in with a registered account to use saved chat")).getId();
    }
    private Conversation owned(String id, String subject) {
        return conversations.lockOwned(id, owner(subject)).orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", id));
    }
    private void projectOwned(String projectId, String ownerId) {
        if (projectId == null) return;
        var project = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        if (!project.getOwner().getId().equals(ownerId)) throw new ForbiddenException("Only your own projects can be attached to saved chat");
    }
    private void recover(Conversation c) {
        for (var m : messages.findByConversationIdAndStatus(c.getId(), "pending")) {
            if (m.getUpdatedAt().isBefore(LocalDateTime.now().minusMinutes(5))) m.setStatus("interrupted");
        }
    }
    private void idle(Conversation c) {
        recover(c);
        if (!messages.findByConversationIdAndStatus(c.getId(), "pending").isEmpty())
            throw new ConflictException("CHAT_GENERATING", "A response is still generating. Stop it or wait before continuing.");
    }
    @Transactional public Summary create(String subject, String projectId) {
        String owner = owner(subject);
        projectOwned(projectId, owner);
        var c = new Conversation(); c.setOwnerId(owner); c.setProjectId(projectId); c.setTitle("New conversation");
        return Summary.of(conversations.saveAndFlush(c));
    }
    @Transactional(readOnly=true) public Page<Summary> list(String subject, String project, String query, int page, int size) {
        String search = query == null ? "" : query.trim();
        if (search.length() > 120) throw new BadRequestException("Search must not exceed 120 characters");
        search = search.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return conversations.search(owner(subject),project,"%"+search+"%",PageRequest.of(Math.max(0,page),Math.clamp(size,1,50),Sort.by(Sort.Order.desc("updatedAt"),Sort.Order.desc("id")))).map(Summary::of);
    }
    @Transactional public Summary get(String subject, String id) { var c=owned(id,subject); recover(c); return Summary.of(c); }
    @Transactional public Page<Message> history(String subject, String id, int page, int size) {
        var c=owned(id,subject); recover(c);
        return messages.findByConversationId(id,PageRequest.of(Math.max(0,page),Math.clamp(size,1,100),Sort.by(Sort.Direction.DESC,"sequenceNumber"))).map(Message::of);
    }
    @Transactional public Summary rename(String subject,String id,String title) {
        var c=owned(id,subject); title=title.strip();
        if(title.isEmpty()||title.length()>200) throw new BadRequestException("Title must contain 1 to 200 characters");
        c.setTitle(title); return Summary.of(conversations.saveAndFlush(c));
    }
    @Transactional public void delete(String subject,String id) {
        var c=owned(id,subject); idle(c); messages.deleteByConversationId(id); conversations.delete(c);
    }
    @Transactional public Turn begin(String subject,String id,ConversationRequests.Send input) {
        var c=owned(id,subject);
        if(input.getMessage()==null||input.getMessage().isBlank()||input.getMessage().length()>6000) throw new BadRequestException("Message must contain 1 to 6000 characters");
        try { UUID.fromString(input.getClientRequestId()); } catch(Exception e) { throw new BadRequestException("A valid request ID is required"); }
        var prior=messages.findByConversationIdAndClientRequestIdAndRole(id,input.getClientRequestId(),"user");
        ConversationMessage user; ConversationMessage assistant;
        if(prior.isPresent()) {
            user=prior.get();
            if(!user.getContent().equals(input.getMessage().strip())) throw new ConflictException("CHAT_REQUEST_REUSED","This request ID belongs to another message");
            assistant=messages.findByConversationIdAndClientRequestIdAndRole(id,input.getClientRequestId(),"assistant").orElseThrow();
            if(assistant.getStatus().equals("completed")) return new Turn(Summary.of(c),Message.of(user),Message.of(assistant),null,null,true);
            idle(c);
            if(!input.isRetry()) return new Turn(Summary.of(c),Message.of(user),Message.of(assistant),null,null,true);
            if(assistant.getSequenceNumber()!=c.getNextSequence()) throw new ConflictException("CHAT_RETRY_STALE","Only the latest response can be retried. Send a new message to continue.");
        } else {
            idle(c);
            user=new ConversationMessage(); user.setConversationId(id); user.setClientRequestId(input.getClientRequestId());
            user.setSequenceNumber(c.getNextSequence()+1); user.setRole("user"); user.setStatus("completed"); user.setContent(input.getMessage().strip());
            assistant=new ConversationMessage(); assistant.setConversationId(id); assistant.setClientRequestId(input.getClientRequestId());
            assistant.setSequenceNumber(c.getNextSequence()+2); assistant.setRole("assistant");
            c.setNextSequence(assistant.getSequenceNumber());
            if(c.getTitle().equals("New conversation")) c.setTitle(user.getContent().replaceAll("\\s+"," ").substring(0,Math.min(80,user.getContent().replaceAll("\\s+"," ").length())));
        }
        AiChatRequest request=input;
        if(!input.isIncludeProjectContext()) request=new AiChatRequest();
        else {
            if(c.getProjectId()==null) throw new BadRequestException("Attach a project before sharing project context");
            projectOwned(c.getProjectId(),c.getOwnerId());
            if(input.getProjectId()!=null&&!input.getProjectId().equals(c.getProjectId())) throw new BadRequestException("Project context does not match this conversation");
            if(input.getProjectRevision()!=null && !input.getProjectRevision().equals(projectService.getProjectRevision(c.getProjectId(),subject)))
                throw new ConflictException("AI_PROJECT_REVISION_STALE", "The project changed. Reload it before sharing project context.");
        }
        request.setMessage(user.getContent()); request.setSessionId(id); request.setAuthenticatedUserId(subject);
        // A project is sent to the provider only after explicit consent, including its bounded memory scope.
        request.setProjectId(input.isIncludeProjectContext()?c.getProjectId():null);
        var history=new ArrayList<>(messages.findByConversationId(id,PageRequest.of(0,22,Sort.by(Sort.Direction.DESC,"sequenceNumber"))).getContent());
        Collections.reverse(history);
        var contextHistory=new ArrayDeque<AiChatRequest.ChatMessage>();
        int contextCharacters=0;
        for(var m:history.reversed()) {
            if(m.getSequenceNumber()>=user.getSequenceNumber()||!m.getStatus().equals("completed")) continue;
            String excerpt=m.getContent().substring(0,Math.min(m.getContent().length(),12000));
            if(contextHistory.size()==20||contextCharacters+excerpt.length()>60000) break;
            contextHistory.addFirst(new AiChatRequest.ChatMessage(m.getRole(),excerpt)); contextCharacters+=excerpt.length();
        }
        request.setHistory(new ArrayList<>(contextHistory));
        request.setMemory(null); request.setRetrievedEvidence(null); policy.validateChatRequest(request);
        String attempt=UUID.randomUUID().toString(); assistant.setAttemptId(attempt); assistant.setStatus("pending"); assistant.setContent(""); assistant.setMetadata(null);
        messages.saveAndFlush(user); messages.saveAndFlush(assistant); c.setUpdatedAt(LocalDateTime.now()); conversations.saveAndFlush(c);
        return new Turn(Summary.of(c),Message.of(user),Message.of(assistant),attempt,request,false);
    }
    @Transactional public Message checkpoint(Turn turn,String content,String status,Map<String,Object> metadata) {
        var c=conversations.lockOwned(turn.conversation().id(), owner(turn.request().getAuthenticatedUserId())).orElseThrow();
        var m=messages.findById(turn.assistant().id()).orElseThrow();
        if(!Objects.equals(m.getAttemptId(),turn.attemptId())||!m.getStatus().equals("pending")) return Message.of(m);
        m.setContent(content.substring(0,Math.min(content.length(),120000))); m.setStatus(status); m.setMetadata(metadata);
        c.setUpdatedAt(LocalDateTime.now()); messages.saveAndFlush(m); return Message.of(m);
    }
    @Transactional public void stop(String subject,String id) {
        var c=owned(id,subject);
        for(var m:messages.findByConversationIdAndStatus(c.getId(),"pending")) m.setStatus("interrupted");
    }
}
