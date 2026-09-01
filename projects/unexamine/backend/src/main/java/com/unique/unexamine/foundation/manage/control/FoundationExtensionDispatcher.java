package com.unique.unexamine.foundation.manage.control;

import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import com.unique.unexamine.foundation.base.service.CoreEventOutboxBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FoundationExtensionDispatcher {
    private final CoreEventOutboxBaseService outboxService;
    private final List<FoundationCommandExtension> extensions;

    public FoundationExtensionDispatcher(CoreEventOutboxBaseService outboxService,
                                         List<FoundationCommandExtension> extensions) {
        this.outboxService = outboxService;
        this.extensions = extensions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(long outboxId) {
        CoreEventOutbox event = outboxService.selectById(outboxId);
        if (event == null || !"PENDING".equals(event.getStatus())) return;
        try {
            for (FoundationCommandExtension extension : extensions) {
                extension.afterCommit(event);
            }
            event.setStatus("PUBLISHED");
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);
        } catch (RuntimeException exception) {
            event.setStatus("FAILED");
            event.setRetryCount((event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1);
            event.setLastError(exception.getMessage() == null ? exception.getClass().getSimpleName()
                    : exception.getMessage().substring(0, Math.min(1900, exception.getMessage().length())));
        }
        outboxService.updateById(event);
    }
}
