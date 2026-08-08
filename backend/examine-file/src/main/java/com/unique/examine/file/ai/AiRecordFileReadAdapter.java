package com.unique.examine.file.ai;

import com.unique.examine.core.ai.AiRecordFileReadFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.file.domain.FileDomainException;
import com.unique.examine.file.domain.RuntimeRecordFileActor;
import com.unique.examine.file.domain.RuntimeRecordFilePage;
import com.unique.examine.file.service.RuntimeRecordFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.util.Objects;

/** File-owned translation from one strict AI request to safe attachment metadata. */
@Component
public class AiRecordFileReadAdapter implements AiRecordFileReadFacade {
    private final FilePageReader files;

    @Autowired
    public AiRecordFileReadAdapter(RuntimeRecordFileService files) {
        this(files::page);
    }

    AiRecordFileReadAdapter(FilePageReader files) {
        this.files = Objects.requireNonNull(files, "files");
    }

    @Override
    @Transactional(readOnly = true)
    public Result query(Request request) {
        Objects.requireNonNull(request, "request");
        var actor = new RuntimeRecordFileActor(
                request.systemId(), request.tenantId(), request.memberId(),
                request.effectivePermissions(), request.moduleCode(),
                Long.parseLong(request.recordId()));
        final RuntimeRecordFilePage page;
        try {
            page = files.page(actor, 1, request.limit());
        } catch (FileDomainException failure) {
            var status = switch (failure.code()) {
                case "FILE_FORBIDDEN" -> HttpStatus.FORBIDDEN;
                case "FILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
                default -> HttpStatus.UNPROCESSABLE_ENTITY;
            };
            throw new BusinessException(
                    failure.code(), failure.getMessage(), status);
        }
        return new Result(
                request.moduleCode(), request.recordId(), page.total(),
                route(request),
                page.items().stream().limit(request.limit()).map(value -> {
                    var asset = value.asset();
                    var reference = value.reference();
                    return new File(
                            Long.toString(asset.id()), asset.originalName(),
                            asset.mediaType(), asset.size(),
                            Long.toString(asset.uploaderMemberId()),
                            asset.createdAt(),
                            reference.createdAt());
                }).toList());
    }

    private static String route(Request request) {
        return "/systems/" + request.systemId() + "/workbench?module="
                + request.moduleCode() + "&mode=view&record=" + request.recordId();
    }

    @FunctionalInterface
    interface FilePageReader {
        RuntimeRecordFilePage page(
                RuntimeRecordFileActor actor,
                int page,
                int size);
    }
}
