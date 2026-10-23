package in.voltforge.api.ai.document;

import in.voltforge.api.common.dto.*;
import in.voltforge.api.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;

@RestController @RequestMapping("/api/v1/ai/documents") @RequiredArgsConstructor
public class DocumentController {
    private final DocumentService documents;
    private final DocumentExtractor extractor;
    @GetMapping public ApiResponse<PagedResponse<DocumentService.Summary>> list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required=false) String projectId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page) {
        var p=documents.list(jwt.getSubject(),projectId,q,page);
        return ApiResponse.success("Documents loaded",PagedResponse.<DocumentService.Summary>builder().content(p.getContent()).page(p.getNumber()).size(p.getSize()).totalElements(p.getTotalElements()).totalPages(p.getTotalPages()).first(p.isFirst()).last(p.isLast()).build());
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentService.Summary> upload(@AuthenticationPrincipal Jwt jwt,@RequestParam MultipartFile file,@RequestParam String title,
        @RequestParam(required=false) String projectId,@RequestParam(required=false) String documentId) throws IOException {
        documents.checkUpload(jwt.getSubject(),projectId,documentId);
        if(file.getSize()>DocumentExtractor.MAX_BYTES) throw new BadRequestException("File exceeds 2 MiB");
        try(var stream=file.getInputStream()) {
            var extracted=extractor.extract(stream.readNBytes(DocumentExtractor.MAX_BYTES+1),file.getOriginalFilename());
            return ApiResponse.success("Document indexed",documents.save(jwt.getSubject(),projectId,documentId,title,extracted));
        }
    }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt,@PathVariable String id) {
        documents.delete(jwt.getSubject(),id); return ApiResponse.success("Document deleted",null);
    }
    @GetMapping("/{id}/versions/{version}/chunks/{chunk}")
    public ApiResponse<Map<String,Object>> source(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@PathVariable String version,@PathVariable String chunk) {
        return ApiResponse.success("Source excerpt loaded",documents.readChunk(jwt.getSubject(),id,version,chunk));
    }
}
