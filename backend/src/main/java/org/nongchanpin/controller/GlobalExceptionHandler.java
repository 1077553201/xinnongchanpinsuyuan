package org.nongchanpin.controller;

import org.nongchanpin.model.CommonResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public CommonResponse handleLargeUpload(Exception ex) { return new CommonResponse("413", "附件不能超过 5 MB，请缩小后重新选择。", null); }
    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,org.springframework.web.multipart.MultipartException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse handleMultipart(Exception ex) { return new CommonResponse("400", "请一并提交报告资料和有效附件。", null); }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse handleMalformedRequest(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return new CommonResponse("400", "请求格式不正确，请检查日期、数字及整数件数。", null);
    }
    @ExceptionHandler(javax.validation.ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse handleConstraint(javax.validation.ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream().findFirst()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage()).orElse("请检查填写内容。");
        return new CommonResponse("400", message, null);
    }
    @ExceptionHandler(org.nongchanpin.security.AccessException.class)
    public org.springframework.http.ResponseEntity<CommonResponse> handleAccess(org.nongchanpin.security.AccessException ex) {
        return org.springframework.http.ResponseEntity.status(ex.getStatus())
                .body(new CommonResponse(String.valueOf(ex.getStatus()), ex.getMessage(), null));
    }
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public CommonResponse handleDuplicate(DuplicateKeyException ex) {
        return new CommonResponse("409", "业务编码或相同资料已存在，请刷新核对原记录。", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse(ex.getMessage());
        return new CommonResponse("400", message, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public CommonResponse handleBadRequest(IllegalArgumentException ex) {
        return new CommonResponse("400", ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public CommonResponse handleException(Exception ex) {
        return new CommonResponse("500", "服务暂时无法处理请求，请稍后重试。", null);
    }
}
