package in.voltforge.api.config;

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

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorizationHeader = firstHeader(accessor, "Authorization");
        if (!StringUtils.hasText(authorizationHeader)) {
            authorizationHeader = firstHeader(accessor, "authorization");
        }

        if (!StringUtils.hasText(authorizationHeader)) {
            return message;
        }

        String tokenValue = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : authorizationHeader;

        try {
            Jwt jwt = jwtDecoder.decode(tokenValue);
            AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);
            if (authentication != null) {
                authentication.setDetails(jwt);
                accessor.setUser(authentication);
                log.debug("Authenticated STOMP session for subject {}", jwt.getSubject());
            }
            return message;
        } catch (JwtException ex) {
            throw new MessagingException("Invalid STOMP bearer token", ex);
        }
    }

    private String firstHeader(StompHeaderAccessor accessor, String headerName) {
        return accessor.getFirstNativeHeader(headerName);
    }
}
