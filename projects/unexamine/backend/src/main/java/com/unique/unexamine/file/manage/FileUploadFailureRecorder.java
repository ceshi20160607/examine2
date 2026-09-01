package com.unique.unexamine.file.manage;

import com.unique.unexamine.file.base.entity.FileUploadSession;
import com.unique.unexamine.file.base.service.FileUploadSessionBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class FileUploadFailureRecorder {
    private final FileUploadSessionBaseService sessionService;

    public FileUploadFailureRecorder(FileUploadSessionBaseService sessionService) {
        this.sessionService = sessionService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long sessionId, String status) {
        FileUploadSession session = sessionService.selectById(sessionId);
        if (session == null || "COMPLETED".equals(session.getStatus())) return;
        session.setStatus(status);
        session.setUpdatedAt(LocalDateTime.now());
        sessionService.updateById(session);
    }
}
