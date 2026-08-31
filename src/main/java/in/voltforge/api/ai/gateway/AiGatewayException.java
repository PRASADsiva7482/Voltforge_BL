package in.voltforge.api.ai.gateway;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A safe, typed error for the Spring-to-local-AI boundary.
 *
 * <p>The public message is deliberately selected by the gateway. Upstream
 * URLs, exception text, credentials, and response bodies never cross this
 * boundary.</p>
 */
@Getter
public class AiGatewayException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;
    private final boolean retryable;

    public AiGatewayException(HttpStatus status, String errorCode, String message, boolean retryable) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
}
