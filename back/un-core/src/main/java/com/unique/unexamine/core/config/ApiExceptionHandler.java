package com.unique.unexamine.core.config;

import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import java.util.NoSuchElementException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 对业务失败返回可理解的状态和原因，避免把不存在与无权错误伪装成服务异常。 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 显式业务异常的响应码由业务定义，HTTP 状态按对应失败类型返回。 */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Result<Void>> business(BaseException ex) {
        int code = ex.getCode();
        HttpStatus status = HttpStatus.resolve(code);
        return ResponseEntity.status(status == null || !status.isError() ? HttpStatus.BAD_REQUEST : status)
                .body(Result.error(ex));
    }

    /** 业务服务明确声明的授权或冲突状态。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> status(ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        return ResponseEntity.status(ex.getStatusCode()).body(Result.error(code,
                ex.getReason() == null ? "请求失败" : ex.getReason()));
    }

    /** 参数无效时提示业务原因。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Result.error(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage()));
    }

    /** 主键查询不到记录时返回不存在。 */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Result<Void>> missing(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(SystemCodeEnum.SYSTEM_NO_FOUND, ex.getMessage()));
    }

    /** 唯一键等数据约束失败属于业务冲突。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> conflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(SystemCodeEnum.SYSTEM_CONFLICT, "数据已存在或被其他业务引用"));
    }

    /** 请求体字段校验失败时说明第一个字段问题。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "请求参数无效" : ex.getBindingResult().getFieldErrors().getFirst().getField() + "无效";
        return ResponseEntity.badRequest().body(Result.error(SystemCodeEnum.SYSTEM_NO_VALID, message));
    }

    /** 请求体格式或路径参数类型不符时统一返回参数错误。 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingRequestHeaderException.class,
            ConstraintViolationException.class})
    public ResponseEntity<Result<Void>> malformed(Exception ex) {
        return ResponseEntity.badRequest().body(Result.error(SystemCodeEnum.SYSTEM_NO_VALID, "请求参数格式无效"));
    }

    /** 未预期错误记录服务端原因，不向调用方泄露内部信息。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unexpected(Exception ex) {
        LOG.error("接口处理失败", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(SystemCodeEnum.SYSTEM_ERROR));
    }
}
