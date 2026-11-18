package com.erhms.workspace;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspace/contracts")
public class ContractDocumentController {
    private final WorkspaceService workspace;
    private final ContractPdfRenderer renderer;

    public ContractDocumentController(WorkspaceService workspace,ContractPdfRenderer renderer){this.workspace=workspace;this.renderer=renderer;}

    @GetMapping("/{id}/pdf")
    @SuppressWarnings("unchecked")
    public ResponseEntity<byte[]> pdf(@PathVariable String id,@RequestParam(defaultValue="true") boolean download){
        Map<String,Object> document=workspace.contractDocument(id);
        String filename="Hop-dong-"+document.get("code").toString().replaceAll("[^\\p{L}\\p{N}._-]","-")+".pdf";
        var disposition=(download?ContentDisposition.attachment():ContentDisposition.inline()).filename(filename,StandardCharsets.UTF_8).build();
        byte[] pdf=document.get("pdf") instanceof byte[] frozen?frozen:renderer.pdf((Map<String,Object>)document.get("values"));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString()).header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header("X-Content-Type-Options","nosniff").body(pdf);
    }
}
