package com.example.letters.document;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.letters.security.SecurityConfig;

import jakarta.persistence.EntityNotFoundException;

/** Both stub document controllers. Visibility itself is tested against the database in DocumentVisibilityTest. */
@WebMvcTest({ExternalDocumentController.class, StaffDocumentController.class})
@Import(SecurityConfig.class)
public class DocumentControllerTest {

    private static final String VERSION = "Api-Version";
    private static final Instant CREATED = Instant.parse("2026-01-15T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void shouldReturnExternalDocuments() throws Exception {
        when(documentService.getExternalDocuments("APP-0001"))
                .thenReturn(List.of(new DocumentResponse("DOC-1", "APP-0001", "LETTER_I", CREATED)));

        mockMvc.perform(get("/api/external/applications/APP-0001/documents").header(VERSION, "1")
                        .with(httpBasic("external", "external")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docId").value("DOC-1"))
                .andExpect(jsonPath("$[0].visibleToExternal").doesNotExist());
    }

    @Test
    void shouldReturnStaffDocumentsWithTheVisibilityFlag() throws Exception {
        when(documentService.getStaffDocuments("APP-0001"))
                .thenReturn(List.of(new StaffDocumentResponse("DOC-1", "APP-0001", "LETTER_A", CREATED, false)));

        mockMvc.perform(get("/api/staff/applications/APP-0001/documents").header(VERSION, "1")
                        .with(httpBasic("staff", "staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].visibleToExternal").value(false));
    }

    @Test
    void shouldDeleteDocument() throws Exception {
        mockMvc.perform(delete("/api/staff/documents/DOC-1").header(VERSION, "1").with(httpBasic("staff", "staff")))
                .andExpect(status().isNoContent());
        verify(documentService).deleteDocumentById("DOC-1");
    }

    @Test
    void shouldReturn404WhenDeletingAnUnknownDocument() throws Exception {
        doThrow(new EntityNotFoundException("Document not found with id: NOPE"))
                .when(documentService).deleteDocumentById("NOPE");

        mockMvc.perform(delete("/api/staff/documents/NOPE").header(VERSION, "1").with(httpBasic("staff", "staff")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // --- Authorisation ---

    @Test
    void staffCannotUseTheExternalEndpoint() throws Exception {
        mockMvc.perform(get("/api/external/applications/APP-0001/documents").header(VERSION, "1")
                        .with(httpBasic("staff", "staff")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documentService);
    }

    @Test
    void externalCannotUseTheStaffList() throws Exception {
        mockMvc.perform(get("/api/staff/applications/APP-0001/documents").header(VERSION, "1")
                        .with(httpBasic("external", "external")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documentService);
    }

    @Test
    void externalCannotDeleteDocuments() throws Exception {
        mockMvc.perform(delete("/api/staff/documents/DOC-1").header(VERSION, "1")
                        .with(httpBasic("external", "external")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(documentService);
    }

    @Test
    void anonymousCannotUseTheExternalEndpoint() throws Exception {
        mockMvc.perform(get("/api/external/applications/APP-0001/documents").header(VERSION, "1"))
                .andExpect(status().isUnauthorized());
    }
}
