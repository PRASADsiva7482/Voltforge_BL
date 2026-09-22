package in.voltforge.api.project.controller;

import in.voltforge.api.project.dto.CreateProjectRequest;
import in.voltforge.api.project.dto.UpdateProjectRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/** Bound JSON bytes before parsing or opening a project transaction, including chunked requests. */
@ControllerAdvice(assignableTypes = ProjectController.class)
public class ProjectRequestBodyAdvice extends RequestBodyAdviceAdapter {
    public static final int MAX_REQUEST_BYTES = 4 * 1024 * 1024;

    public static class PayloadTooLargeException extends RuntimeException {
        public PayloadTooLargeException() {
            super("Project save exceeds the 4 MiB request limit. Reduce the document size and save again.");
        }
    }

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == CreateProjectRequest.class || targetType == UpdateProjectRequest.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage message, MethodParameter parameter, Type targetType,
                                          Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        // Read only the bounded prefix even when Content-Length is known. An
        // immediate rejection can make Tomcat reset the socket while a normal
        // browser is still sending, hiding the useful 413 response from it.
        byte[] body = message.getBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) throw new PayloadTooLargeException();
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() { return message.getHeaders(); }
        };
    }
}
