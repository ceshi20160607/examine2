package com.unique.examine.module.runtime.recent;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;

@Service
public class RecentService {
    private final RecentRepository repository;
    private final RecentRequestParser parser;
    private final RecentTargetResolver targets;

    public RecentService(
            RecentRepository repository,
            RecentRequestParser parser,
            RecentTargetResolver targets
    ) {
        this.repository = repository;
        this.parser = parser;
        this.targets = targets;
    }

    @Transactional
    public RecentViews.RecentItem touch(RuntimeSession session, String body) {
        var request = parser.touch(body);
        var target = targets.resolve(session, request.moduleCode(), request.recordId());
        return item(repository.touch(session, target), target);
    }

    public RecentViews.RecentPage list(RuntimeSession session, int page, int size) {
        var request = parser.page(page, size);
        var visible = new ArrayList<RecentViews.RecentItem>();
        for (var stored : repository.list(session)) {
            resolveVisible(session, stored).ifPresent(visible::add);
        }
        var offset = (long) (request.page() - 1) * request.size();
        if (offset >= visible.size()) {
            return new RecentViews.RecentPage(
                    java.util.List.of(), request.page(), request.size(), visible.size());
        }
        var from = (int) offset;
        var to = Math.min(from + request.size(), visible.size());
        return new RecentViews.RecentPage(
                visible.subList(from, to), request.page(), request.size(), visible.size());
    }

    private Optional<RecentViews.RecentItem> resolveVisible(
            RuntimeSession session,
            RecentRepository.StoredRecent stored
    ) {
        try {
            var target = targets.resolve(session, stored.moduleCode(), stored.recordId());
            return Optional.of(item(stored, target));
        } catch (BusinessException exception) {
            return Optional.empty();
        }
    }

    private static RecentViews.RecentItem item(
            RecentRepository.StoredRecent stored,
            RecentTargetResolver.ResolvedTarget target
    ) {
        return new RecentViews.RecentItem(
                Long.toString(stored.id()),
                target.moduleCode(),
                Long.toString(stored.recordId()),
                target.displayLabel(),
                target.status(),
                stored.accessCount(),
                stored.lastAccessedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
