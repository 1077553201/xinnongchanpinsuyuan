package org.nongchanpin.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.validation.Valid;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.nongchanpin.model.CommonResponse;
import org.nongchanpin.model.dto.SourceQualityRequests.*;
import org.nongchanpin.model.entity.SourceQualityModels.Report;
import org.nongchanpin.service.SourceQualityService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class SourceQualityController {
    private final SourceQualityService service;
    @GetMapping("/api/production/{source:harvests|packings}/{sourceId}/quality-reports")
    public CommonResponse list(@PathVariable String source,@PathVariable Long sourceId,@RequestParam(defaultValue="1")int page){return CommonResponse.ok(service.list(kind(source),sourceId,page));}
    @PostMapping(value="/api/production/{source:harvests|packings}/{sourceId}/quality-reports",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public CommonResponse create(@PathVariable String source,@PathVariable Long sourceId,@Valid @RequestPart("report")Create report,@RequestPart("attachment")MultipartFile attachment){return CommonResponse.ok(service.create(kind(source),sourceId,report,attachment));}
    @PostMapping("/api/production/quality-reports/{id}/publish")
    public CommonResponse publish(@PathVariable Long id,@Valid @RequestBody Publish request){return CommonResponse.ok(service.publish(id,request));}
    @PostMapping("/api/production/quality-reports/{id}/withdraw")
    public CommonResponse withdraw(@PathVariable Long id,@Valid @RequestBody Withdraw request){return CommonResponse.ok(service.withdraw(id,request));}
    @GetMapping("/api/production/quality-reports/{id}/attachment")
    public ResponseEntity<byte[]> attachment(@PathVariable Long id,HttpServletResponse response){Report row=service.privateReport(id);return download(service.privateContent(id),row.getFileType(),row.getFileName(),response);}
    @GetMapping("/api/public/trace/{code}/quality-reports/{id}/attachment")
    public ResponseEntity<byte[]> publicAttachment(@PathVariable String code,@PathVariable Long id,HttpServletResponse response){Map<String,Object> file=service.publicContent(code,id);return download((byte[])file.get("content"),file.get("fileType").toString(),file.get("fileName").toString(),response);}
    private String kind(String source){return "harvests".equals(source)?"HARVEST":"PACKING";}
    private ResponseEntity<byte[]> download(byte[] content,String type,String name,HttpServletResponse response){response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Content-Security-Policy","sandbox");return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).contentLength(content.length).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString()).body(content);}
}
