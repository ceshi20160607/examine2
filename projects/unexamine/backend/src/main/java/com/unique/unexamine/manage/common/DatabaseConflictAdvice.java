package com.unique.unexamine.manage.common;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** 将数据库唯一约束冲突转成客户端可处理的业务冲突，不泄露表名和 SQL。 */
@RestControllerAdvice
public class DatabaseConflictAdvice {
    /** 并发创建同一编码或关系时，数据库约束仍是最终裁决。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, String>> duplicate(DuplicateKeyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "编码或关系已存在"));
    }
}
