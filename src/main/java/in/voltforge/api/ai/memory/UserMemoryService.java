package in.voltforge.api.ai.memory;

import in.voltforge.api.common.exception.*;
import in.voltforge.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class UserMemoryService {
    private final UserMemoryRepository memories;
    private final UserRepository users;
    public static final Map<String,List<String>> OPTIONS=Map.of(
        "response_detail",List.of("concise","detailed"),
        "units",List.of("metric","imperial"),
        "experience",List.of("beginner","intermediate","advanced"));
    public static final String HELP="Persistent memory supports only response detail (concise or detailed), units (metric or imperial), and electronics experience (beginner, intermediate, or advanced). Say, for example, 'Remember I prefer concise answers', or use Memory to review, edit, or delete preferences. Chat history is saved separately. Arbitrary facts, project data, credentials, and sensitive information are not stored as memory.";
    private String owner(String subject,boolean lock) {
        return (lock?users.lockMemoryOwner(subject):users.findByKeycloakId(subject))
            .orElseThrow(()->new ForbiddenException("Sign in with a registered account to use memory")).getId();
    }
    private void validate(String key,String value) {
        if(!OPTIONS.containsKey(key)||!OPTIONS.get(key).contains(value)) throw new BadRequestException("Unsupported memory preference. Use the listed options; free text is not stored.");
    }
    @Transactional(readOnly=true) public Map<String,String> list(String subject) {
        var result=new TreeMap<String,String>();
        memories.findByOwnerIdOrderByPreferenceKey(owner(subject,false)).forEach(m->result.put(m.getPreferenceKey(),m.getPreferenceValue()));
        return result;
    }
    @Transactional public Map<String,String> put(String subject,String key,String value) {
        validate(key,value);
        String ownerId=owner(subject,true); // Serializes upserts, including the first preference.
        var m=memories.findByOwnerIdAndPreferenceKey(ownerId,key).orElseGet(UserMemory::new);
        m.setOwnerId(ownerId); m.setPreferenceKey(key); m.setPreferenceValue(value);
        memories.saveAndFlush(m);
        return list(subject);
    }
    @Transactional public void delete(String subject,String key) {
        if(!OPTIONS.containsKey(key)) throw new BadRequestException("Unsupported memory preference");
        memories.findByOwnerIdAndPreferenceKey(owner(subject,true),key).ifPresent(memories::delete);
        memories.flush();
    }
    /** Narrow commands avoid accidentally storing a quoted request, secret, or inferred fact. */
    @Transactional public Optional<String> command(String subject,String message) {
        String text=message.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").replaceFirst("[.!]$","");
        if(!text.matches("^(please )?(remember\\b.*|save\\b.*\\bmemory.*|.*\\bto (your )?memory)$")) return Optional.empty();
        String key=null,value=null;
        var detail=java.util.regex.Pattern.compile("^(?:please )?remember (?:that )?i prefer (concise|detailed) (?:answers|responses|explanations)$").matcher(text);
        var units=java.util.regex.Pattern.compile("^(?:please )?remember (?:that )?i prefer (metric|imperial) units$").matcher(text);
        var experience=java.util.regex.Pattern.compile("^(?:please )?remember (?:that )?i(?: am|'m) (?:an? )?(beginner|intermediate|advanced)(?: in electronics)?$").matcher(text);
        if(detail.matches()) {key="response_detail";value=detail.group(1);}
        else if(units.matches()) {key="units";value=units.group(1);}
        else if(experience.matches()) {key="experience";value=experience.group(1);}
        if(key==null) return Optional.of("What preference would you like me to remember? "+HELP);
        put(subject,key,value);
        return Optional.of("Saved your "+key.replace('_',' ')+" preference as "+value+". It will be used across your conversations, including general chats. You can change or delete it in Memory. No project data or transcript was saved as memory.");
    }
}
