package in.voltforge.api.config;

import in.voltforge.api.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final ProjectService projectService;

    private static final Pattern PROJECT_DESTINATION = Pattern.compile(
            "^/(?:app|topic)/project/([^/]+)(?:/.*)?$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            authenticate(accessor);
            return message;
        }

        if (command == null || command == StompCommand.DISCONNECT) {
            return message;
        }

        if (accessor.getUser() == null) {
            throw new MessagingException("STOMP authentication is required");
        }

        if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
            authorizeProjectDestination(accessor, command);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorizationHeader = firstHeader(accessor, "Authorization");
        if (!StringUtils.hasText(authorizationHeader)) {
            authorizationHeader = firstHeader(accessor, "authorization");
        }

        if (!StringUtils.hasText(authorizationHeader)) {
            throw new MessagingException("STOMP bearer token is required");
        }

        String tokenValue = authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorizationHeader.substring(7).trim()
                : authorizationHeader.trim();
        if (!StringUtils.hasText(tokenValue)) {
            throw new MessagingException("STOMP bearer token is required");
        }

        try {
            Jwt jwt = jwtDecoder.decode(tokenValue);
            AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);
            if (authentication != null) {
                authentication.setDetails(jwt);
                accessor.setUser(authentication);
                log.debug("Authenticated STOMP session for subject {}", jwt.getSubject());
                return;
            }
            throw new MessagingException("Unable to authenticate STOMP session");
        } catch (JwtException ex) {
            throw new MessagingException("Invalid STOMP bearer token", ex);
        }
    }

    private void authorizeProjectDestination(StompHeaderAccessor accessor, StompCommand command) {
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            throw new MessagingException("STOMP project destination is required");
        }

        Matcher matcher = PROJECT_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return;
        }

        Principal principal = accessor.getUser();
        String projectId = matcher.group(1);
        boolean allowed;
        if (command == StompCommand.SEND
                && (destination.endsWith("/canvas.update")
                || destination.endsWith("/simulation.status"))) {
            allowed = projectService.canEditProject(projectId, principal.getName());
        } else {
            allowed = projectService.canAccessProject(projectId, principal.getName());
        }

        if (!allowed) {
            throw new MessagingException("Access denied to project " + projectId);
        }
    }

    private String firstHeader(StompHeaderAccessor accessor, String headerName) {
        return accessor.getFirstNativeHeader(headerName);
    }
}
