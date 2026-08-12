package com.unique.examine.collab.recordteam;

import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

public final class RecordTeamException extends BusinessException {
    private RecordTeamException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    static RecordTeamException badRequest(String code, String message) {
        return new RecordTeamException(code, message, HttpStatus.BAD_REQUEST);
    }

    static RecordTeamException forbidden(String message) {
        return new RecordTeamException("RECORD_TEAM_PERMISSION_DENIED", message, HttpStatus.FORBIDDEN);
    }

    static RecordTeamException notFound(String code, String message) {
        return new RecordTeamException(code, message, HttpStatus.NOT_FOUND);
    }

    static RecordTeamException conflict(String code, String message) {
        return new RecordTeamException(code, message, HttpStatus.CONFLICT);
    }
}
