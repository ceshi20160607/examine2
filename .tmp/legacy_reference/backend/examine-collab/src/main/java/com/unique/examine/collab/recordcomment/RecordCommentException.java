package com.unique.examine.collab.recordcomment;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

public final class RecordCommentException extends BusinessException {
    private RecordCommentException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    static RecordCommentException badRequest(String code, String message) {
        return new RecordCommentException(code, message, HttpStatus.BAD_REQUEST);
    }

    static RecordCommentException forbidden(String code, String message) {
        return new RecordCommentException(code, message, HttpStatus.FORBIDDEN);
    }

    static RecordCommentException notFound(String code, String message) {
        return new RecordCommentException(code, message, HttpStatus.NOT_FOUND);
    }

    static RecordCommentException conflict(String code, String message) {
        return new RecordCommentException(code, message, HttpStatus.CONFLICT);
    }
}
