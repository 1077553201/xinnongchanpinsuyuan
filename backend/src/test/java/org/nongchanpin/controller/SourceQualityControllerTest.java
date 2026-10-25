package org.nongchanpin.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.*;
import org.nongchanpin.model.dto.SourceQualityRequests.Create;
import org.nongchanpin.model.entity.SourceQualityModels.Report;
import org.nongchanpin.service.SourceQualityService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SourceQualityControllerTest {
    private final SourceQualityService service=mock(SourceQualityService.class);
    private final MockMvc mvc=MockMvcBuilders.standaloneSetup(new SourceQualityController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
    @Test void multipartMetadataAndAttachmentAreParsedAsOneRequest() throws Exception {
        Report row=new Report();row.setId(1L);when(service.create(eq("PACKING"),eq(223L),any(),any())).thenReturn(row);
        String json="{\"reportNo\":\"QA\",\"agencyName\":\"QA lab\",\"sampledOn\":\"2026-10-25\",\"checkedOn\":\"2026-10-25\",\"declaredResult\":\"INCONCLUSIVE\",\"requestKey\":\"00000000-0000-4000-8000-000000000001\"}";
        mvc.perform(multipart("/api/production/packings/223/quality-reports").file(new MockMultipartFile("report","report.json","application/json",json.getBytes())).file(new MockMultipartFile("attachment","QA.pdf","application/pdf","%PDF-1.4\nQA\n%%EOF".getBytes()))).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(1));
        verify(service).create(eq("PACKING"),eq(223L),argThat((Create r)->r.getDeclaredResult().equals("INCONCLUSIVE")),any());
    }
    @Test void missingMultipartReportIsActionableBadRequest() throws Exception {mvc.perform(multipart("/api/production/harvests/17/quality-reports").file(new MockMultipartFile("attachment","QA.pdf","application/pdf",new byte[]{1}))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("400"));verifyNoInteractions(service);}
    @Test void downloadUsesAttachmentNoStoreAndNosniffHeaders() throws Exception {Report row=new Report();row.setFileName("报告.pdf");row.setFileType("application/pdf");when(service.privateReport(1L)).thenReturn(row);when(service.privateContent(1L)).thenReturn(new byte[]{1,2,3});mvc.perform(get("/api/production/quality-reports/1/attachment")).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Security-Policy","sandbox")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;"))).andExpect(content().bytes(new byte[]{1,2,3}));}
}
