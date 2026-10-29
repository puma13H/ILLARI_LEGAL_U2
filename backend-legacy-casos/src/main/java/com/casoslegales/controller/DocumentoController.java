package com.casoslegales.controller;

import com.casoslegales.dto.*;
import com.casoslegales.service.DocumentoService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

    private final DocumentoService documentoService;

    public DocumentoController(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    @GetMapping("/caso/{casoId}")
    @PreAuthorize("hasAnyRole('admin', 'abogado', 'cliente')")
    public ResponseEntity<List<DocumentoResponse>> listByCaso(@PathVariable Long casoId, Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        return ResponseEntity.ok(documentoService.listByCaso(casoId, auth.getName(), role));
    }

    @PostMapping(value = "/caso/{casoId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('admin', 'abogado')")
    public ResponseEntity<DocumentoResponse> upload(@PathVariable Long casoId,
                                                      @RequestParam String nombre,
                                                      @RequestParam MultipartFile archivo,
                                                      Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        return ResponseEntity.ok(documentoService.upload(casoId, nombre, archivo, auth.getName(), role));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('admin', 'abogado', 'cliente')")
    public ResponseEntity<Resource> download(@PathVariable Long id, Authentication auth) {
        String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        DocumentoResource docResource = documentoService.download(id, auth.getName(), role);
        
        String filename = docResource.nombre() + "." + docResource.extension();
        MediaType mediaType = getMediaType(docResource.extension());
        
        System.out.println("=== DESCARGA DE DOCUMENTO ===");
        System.out.println("ID: " + id);
        System.out.println("Nombre BD: " + docResource.nombre());
        System.out.println("Extensión: " + docResource.extension());
        System.out.println("Filename final: " + filename);
        System.out.println("Content-Type: " + mediaType);
        System.out.println("============================");
        
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(docResource.resource());
    }
    
    private MediaType getMediaType(String extension) {
        return switch (extension.toLowerCase()) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "txt" -> MediaType.TEXT_PLAIN;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "zip" -> MediaType.valueOf("application/zip");
            case "csv" -> MediaType.valueOf("text/csv");
            case "doc", "docx" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "xls", "xlsx" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "ppt", "pptx" -> MediaType.valueOf("application/vnd.openxmlformats-officedocument.presentationml.presentation");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
