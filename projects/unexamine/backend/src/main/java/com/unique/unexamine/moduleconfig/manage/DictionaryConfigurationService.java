package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.moduleconfig.base.entity.CfgDictionary;
import com.unique.unexamine.moduleconfig.base.entity.CfgDictionaryItem;
import com.unique.unexamine.moduleconfig.base.entity.CfgDictionaryPublication;
import com.unique.unexamine.moduleconfig.base.entity.CfgDictionaryVersion;
import com.unique.unexamine.moduleconfig.base.service.CfgDictionaryBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgDictionaryItemBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgDictionaryPublicationBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgDictionaryVersionBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DictionaryConfigurationService {
    private final CfgDictionaryBaseService dictionaryService;
    private final CfgDictionaryItemBaseService itemService;
    private final CfgDictionaryVersionBaseService versionService;
    private final CfgDictionaryPublicationBaseService publicationService;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public DictionaryConfigurationService(
            CfgDictionaryBaseService dictionaryService,
            CfgDictionaryItemBaseService itemService,
            CfgDictionaryVersionBaseService versionService,
            CfgDictionaryPublicationBaseService publicationService,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            JdbcTemplate jdbc) {
        this.dictionaryService = dictionaryService;
        this.itemService = itemService;
        this.versionService = versionService;
        this.publicationService = publicationService;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<DictionaryModels.DictionaryDraft> list(AuthenticatedContext context) {
        requireContext(context);
        return dictionaryService.selectList(Wrappers.<CfgDictionary>lambdaQuery()
                        .eq(CfgDictionary::getSystemId, context.systemId())
                        .eq(CfgDictionary::getOwnerTenantId, context.tenantId())
                        .orderByAsc(CfgDictionary::getName, CfgDictionary::getId))
                .stream().map(dictionary -> draft(context, dictionary.getId())).toList();
    }

    @Transactional
    public DictionaryModels.DictionaryDraft create(
            AuthenticatedContext context, DictionaryModels.CreateDictionaryRequest request, String traceId) {
        requireContext(context);
        CfgDictionary dictionary = new CfgDictionary();
        dictionary.setSystemId(context.systemId());
        dictionary.setOwnerTenantId(context.tenantId());
        dictionary.setCode(normalize(request.code()));
        dictionary.setName(request.name().strip());
        dictionary.setHierarchical(request.hierarchical());
        dictionary.setStatus("DRAFT");
        dictionary.setCreatedByMemberId(context.memberId());
        dictionary.setVersion(0);
        try {
            dictionaryService.insert(dictionary);
        } catch (DuplicateKeyException exception) {
            throw new DomainException("DICTIONARY_CODE_CONFLICT", "字典编码在当前租户中已存在", HttpStatus.CONFLICT);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "DICTIONARY_DRAFT_CREATED", "DICTIONARY", dictionary.getId().toString(), "SUCCESS",
                Map.of("code", dictionary.getCode(), "hierarchical", dictionary.getHierarchical()));
        return draft(context, dictionary.getId());
    }

    @Transactional(readOnly = true)
    public DictionaryModels.DictionaryDraft draft(AuthenticatedContext context, Long dictionaryId) {
        CfgDictionary dictionary = requireOwned(context, dictionaryId);
        List<CfgDictionaryItem> items = items(dictionaryId);
        CfgDictionaryPublication publication = publication(dictionaryId);
        CfgDictionaryVersion current = publication == null ? null : versionService.selectById(publication.getCurrentVersionId());
        return new DictionaryModels.DictionaryDraft(dictionary, items,
                current == null ? null : current.getId(), current == null ? null : current.getVersionNumber(),
                publication == null ? null : publication.getVersion());
    }

    @Transactional
    public CfgDictionaryItem createItem(
            AuthenticatedContext context,
            Long dictionaryId,
            DictionaryModels.CreateItemRequest request,
            String traceId) {
        CfgDictionary dictionary = requireOwned(context, dictionaryId);
        CfgDictionaryItem parent = validateParent(dictionary, null, request.parentId());
        CfgDictionaryItem item = new CfgDictionaryItem();
        item.setDictionaryId(dictionaryId);
        item.setParentId(parent == null ? null : parent.getId());
        item.setCode(normalize(request.code()));
        item.setLabel(request.label().strip());
        item.setPathCode(path(parent, item.getCode()));
        item.setColor(blankToNull(request.color()));
        item.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        item.setStatus("ACTIVE");
        item.setVersion(0);
        try {
            itemService.insert(item);
        } catch (DuplicateKeyException exception) {
            throw new DomainException("DICTIONARY_ITEM_CODE_CONFLICT", "字典项编码已存在", HttpStatus.CONFLICT);
        }
        touch(dictionary);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "DICTIONARY_ITEM_DRAFT_CREATED", "DICTIONARY_ITEM", item.getId().toString(), "SUCCESS",
                Map.of("dictionaryId", dictionaryId, "code", item.getCode(), "path", item.getPathCode()));
        return item;
    }

    @Transactional
    public CfgDictionaryItem updateItem(
            AuthenticatedContext context,
            Long dictionaryId,
            Long itemId,
            DictionaryModels.UpdateItemRequest request,
            String traceId) {
        CfgDictionary dictionary = requireOwned(context, dictionaryId);
        CfgDictionaryItem item = requireItem(dictionaryId, itemId);
        if (!request.version().equals(item.getVersion())) {
            throw conflict("字典项草稿已被其他操作修改");
        }
        CfgDictionaryItem parent = validateParent(dictionary, item, request.parentId());
        if ("DISABLED".equals(request.status()) && !"DISABLED".equals(item.getStatus())) {
            long used = itemUsage(dictionaryId, item.getCode());
            if (used > 0) {
                throw new DomainException("DICTIONARY_ITEM_IN_USE",
                        "字典项已有 " + used + " 条运行数据引用，不能直接停用", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        String previousPath = item.getPathCode();
        item.setParentId(parent == null ? null : parent.getId());
        item.setLabel(request.label().strip());
        item.setColor(blankToNull(request.color()));
        item.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        item.setStatus(request.status());
        item.setPathCode(path(parent, item.getCode()));
        if (itemService.updateById(item) == 0) {
            throw conflict("字典项草稿已被其他操作修改");
        }
        if (!previousPath.equals(item.getPathCode())) {
            rewriteDescendantPaths(dictionaryId, previousPath, item.getPathCode());
        }
        touch(dictionary);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "DICTIONARY_ITEM_DRAFT_UPDATED", "DICTIONARY_ITEM", itemId.toString(), "SUCCESS",
                Map.of("dictionaryId", dictionaryId, "status", item.getStatus(), "path", item.getPathCode()));
        return item;
    }

    @Transactional(readOnly = true)
    public DictionaryModels.PublicationCheck check(AuthenticatedContext context, Long dictionaryId) {
        CfgDictionary dictionary = requireOwned(context, dictionaryId);
        List<DictionaryModels.PublicationIssue> issues = validate(dictionary, items(dictionaryId));
        return new DictionaryModels.PublicationCheck(issues.isEmpty(), dictionary.getVersion(), issues);
    }

    @Transactional
    public DictionaryModels.PublishedDictionary publish(
            AuthenticatedContext context,
            Long dictionaryId,
            DictionaryModels.PublishDictionaryRequest request,
            String traceId) {
        CfgDictionary dictionary = requireOwned(context, dictionaryId);
        if (!request.expectedDraftRevision().equals(dictionary.getVersion())) {
            throw conflict("字典草稿已经变化，请重新执行发布检查");
        }
        List<CfgDictionaryItem> items = items(dictionaryId);
        List<DictionaryModels.PublicationIssue> issues = validate(dictionary, items);
        if (!issues.isEmpty()) {
            throw new DomainException("DICTIONARY_PUBLICATION_CHECK_FAILED", issues.getFirst().message(),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.set("dictionary", objectMapper.valueToTree(dictionary));
        snapshot.set("items", objectMapper.valueToTree(items));
        String snapshotJson = write(snapshot);
        int versionNumber = versionService.selectList(Wrappers.<CfgDictionaryVersion>lambdaQuery()
                        .eq(CfgDictionaryVersion::getDictionaryId, dictionaryId))
                .stream().map(CfgDictionaryVersion::getVersionNumber).max(Integer::compareTo).orElse(0) + 1;
        CfgDictionaryVersion version = new CfgDictionaryVersion();
        version.setSystemId(context.systemId());
        version.setOwnerTenantId(context.tenantId());
        version.setDictionaryId(dictionaryId);
        version.setVersionNumber(versionNumber);
        version.setDraftRevision(dictionary.getVersion());
        version.setSnapshotJson(snapshotJson);
        version.setSnapshotHash(sha256(snapshotJson));
        version.setPublishedByMemberId(context.memberId());
        version.setPublishedAt(LocalDateTime.now());
        versionService.insert(version);

        CfgDictionaryPublication publication = publication(dictionaryId);
        if (publication == null) {
            publication = new CfgDictionaryPublication();
            publication.setSystemId(context.systemId());
            publication.setOwnerTenantId(context.tenantId());
            publication.setDictionaryId(dictionaryId);
            publication.setCurrentVersionId(version.getId());
            publication.setPublishedByMemberId(context.memberId());
            publication.setPublishedAt(LocalDateTime.now());
            publication.setVersion(0);
            publicationService.insert(publication);
        } else {
            publication.setCurrentVersionId(version.getId());
            publication.setPublishedByMemberId(context.memberId());
            publication.setPublishedAt(LocalDateTime.now());
            if (publicationService.updateById(publication) == 0) {
                throw conflict("字典发布指针已被其他操作修改");
            }
        }
        if (!"ACTIVE".equals(dictionary.getStatus())) {
            dictionary.setStatus("ACTIVE");
            if (dictionaryService.updateById(dictionary) == 0) {
                throw conflict("字典草稿已被其他操作修改");
            }
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "DICTIONARY_PUBLISHED", "DICTIONARY_VERSION", version.getId().toString(), "SUCCESS",
                Map.of("dictionaryId", dictionaryId, "versionNumber", versionNumber,
                        "draftRevision", version.getDraftRevision(), "itemCount", items.size()));
        CfgDictionaryPublication saved = publication(dictionaryId);
        return new DictionaryModels.PublishedDictionary(dictionaryId, version.getId(), versionNumber,
                saved.getVersion(), snapshot);
    }

    @Transactional(readOnly = true)
    public List<DictionaryModels.VersionSummary> versions(AuthenticatedContext context, Long dictionaryId) {
        requireOwned(context, dictionaryId);
        CfgDictionaryPublication publication = publication(dictionaryId);
        Long currentId = publication == null ? null : publication.getCurrentVersionId();
        int publicationVersion = publication == null ? 0 : publication.getVersion();
        return versionService.selectList(Wrappers.<CfgDictionaryVersion>lambdaQuery()
                        .eq(CfgDictionaryVersion::getDictionaryId, dictionaryId)
                        .orderByDesc(CfgDictionaryVersion::getVersionNumber))
                .stream().map(version -> new DictionaryModels.VersionSummary(version.getId(), version.getVersionNumber(),
                        version.getDraftRevision(), version.getPublishedAt(), version.getId().equals(currentId),
                        publicationVersion)).toList();
    }

    @Transactional(readOnly = true)
    public DictionaryModels.RuntimeDictionary runtime(AuthenticatedContext context, String code, Long parentId) {
        requireContext(context);
        List<CfgDictionary> candidates = dictionaryService.selectList(Wrappers.<CfgDictionary>lambdaQuery()
                        .eq(CfgDictionary::getSystemId, context.systemId())
                        .eq(CfgDictionary::getCode, normalize(code))
                        .eq(CfgDictionary::getStatus, "ACTIVE"));
        Long mainTenantId = mainTenantId(context.systemId());
        CfgDictionary dictionary = candidates.stream()
                .filter(item -> context.tenantId().equals(item.getOwnerTenantId()))
                .findFirst().or(() -> candidates.stream().filter(item -> mainTenantId.equals(item.getOwnerTenantId())).findFirst())
                .orElseThrow(() -> notFound("字典不存在或尚未发布"));
        return runtimeDictionary(dictionary, parentId);
    }

    @Transactional(readOnly = true)
    public DictionaryModels.RuntimeDictionary runtime(AuthenticatedContext context, Long dictionaryId, Long parentId) {
        requireContext(context);
        CfgDictionary dictionary = runtimeDictionary(context, dictionaryId);
        return runtimeDictionary(dictionary, parentId);
    }

    @Transactional(readOnly = true)
    public List<Long> validateRuntimeSelection(
            AuthenticatedContext context,
            Long dictionaryId,
            List<Long> submittedPath,
            int maximumDepth,
            boolean allowIntermediate) {
        if (submittedPath == null || submittedPath.isEmpty() || submittedPath.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("级联选择不能为空");
        }
        try {
            CfgDictionary dictionary = runtimeDictionary(context, dictionaryId);
            CfgDictionaryPublication publication = publication(dictionary.getId());
            CfgDictionaryVersion version = publication == null ? null : versionService.selectById(publication.getCurrentVersionId());
            if (version == null) throw notFound("字典尚未发布");
            JsonNode snapshot = objectMapper.readTree(version.getSnapshotJson());
            Map<Long, JsonNode> activeItems = new HashMap<>();
            for (JsonNode item : snapshot.path("items")) {
                if ("ACTIVE".equals(item.path("status").asText())) activeItems.put(item.path("id").asLong(), item);
            }
            Long selectedId = submittedPath.getLast();
            if (!activeItems.containsKey(selectedId)) throw new IllegalArgumentException("级联选项不存在或已停用");
            List<Long> resolvedPath = new ArrayList<>();
            Set<Long> visited = new HashSet<>();
            Long current = selectedId;
            while (current != null) {
                if (!visited.add(current)) throw new IllegalArgumentException("级联选项层级存在循环");
                JsonNode item = activeItems.get(current);
                if (item == null) throw new IllegalArgumentException("级联选项的父级不存在或已停用");
                resolvedPath.add(current);
                current = item.path("parentId").isNull() ? null : item.path("parentId").asLong();
            }
            java.util.Collections.reverse(resolvedPath);
            if (submittedPath.size() > 1 && !resolvedPath.equals(submittedPath)) {
                throw new IllegalArgumentException("级联选择路径与发布字典不一致");
            }
            if (maximumDepth > 0 && resolvedPath.size() > maximumDepth) {
                throw new IllegalArgumentException("级联选择超过配置的最大层级");
            }
            boolean hasActiveChild = activeItems.values().stream()
                    .anyMatch(item -> !item.path("parentId").isNull() && item.path("parentId").asLong() == selectedId);
            if (!allowIntermediate && hasActiveChild) throw new IllegalArgumentException("当前字段只能选择末级字典项");
            return List.copyOf(resolvedPath);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("字典发布快照无法读取");
        } catch (DomainException exception) {
            throw new IllegalArgumentException(exception.getMessage());
        }
    }

    private CfgDictionary runtimeDictionary(AuthenticatedContext context, Long dictionaryId) {
        requireContext(context);
        CfgDictionary dictionary = dictionaryService.selectById(dictionaryId);
        Long mainTenantId = mainTenantId(context.systemId());
        if (dictionary == null || !context.systemId().equals(dictionary.getSystemId())
                || (!context.tenantId().equals(dictionary.getOwnerTenantId()) && !mainTenantId.equals(dictionary.getOwnerTenantId()))
                || !"ACTIVE".equals(dictionary.getStatus())) {
            throw notFound("字典不存在或尚未发布");
        }
        return dictionary;
    }

    private DictionaryModels.RuntimeDictionary runtimeDictionary(CfgDictionary dictionary, Long parentId) {
        CfgDictionaryPublication publication = publication(dictionary.getId());
        if (publication == null) throw notFound("字典尚未发布");
        CfgDictionaryVersion version = versionService.selectById(publication.getCurrentVersionId());
        if (version == null) throw notFound("字典发布版本不存在");
        try {
            JsonNode snapshot = objectMapper.readTree(version.getSnapshotJson());
            List<JsonNode> selectable = new ArrayList<>();
            for (JsonNode item : snapshot.path("items")) {
                boolean sameParent = parentId == null ? item.path("parentId").isNull()
                        : item.path("parentId").asLong(-1) == parentId;
                if (sameParent && "ACTIVE".equals(item.path("status").asText())) {
                    selectable.add(item);
                }
            }
            return new DictionaryModels.RuntimeDictionary(dictionary.getId(), dictionary.getCode(), dictionary.getName(),
                    Boolean.TRUE.equals(dictionary.getHierarchical()), version.getId(), version.getVersionNumber(),
                    publication.getVersion(), selectable);
        } catch (JsonProcessingException exception) {
            throw new DomainException("DICTIONARY_SNAPSHOT_INVALID", "字典发布快照无法读取", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Long mainTenantId(Long systemId) {
        List<Long> ids = jdbc.queryForList(
                "select id from sys_tenant where system_id=? and is_main=1 and status='ACTIVE' order by id limit 1",
                Long.class, systemId);
        return ids.isEmpty() ? -1L : ids.getFirst();
    }

    private List<DictionaryModels.PublicationIssue> validate(CfgDictionary dictionary, List<CfgDictionaryItem> items) {
        List<DictionaryModels.PublicationIssue> issues = new ArrayList<>();
        if (items.stream().noneMatch(item -> "ACTIVE".equals(item.getStatus()))) {
            issues.add(new DictionaryModels.PublicationIssue("items", "ACTIVE_DICTIONARY_ITEM_REQUIRED", "至少需要一个启用的字典项"));
        }
        Map<Long, CfgDictionaryItem> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.getId(), item));
        Set<String> paths = new HashSet<>();
        for (CfgDictionaryItem item : items) {
            String path = "items." + item.getCode();
            if (!paths.add(item.getPathCode())) {
                issues.add(new DictionaryModels.PublicationIssue(path, "DICTIONARY_PATH_DUPLICATED", "字典项路径重复"));
            }
            if (!Boolean.TRUE.equals(dictionary.getHierarchical()) && item.getParentId() != null) {
                issues.add(new DictionaryModels.PublicationIssue(path + ".parentId", "FLAT_DICTIONARY_PARENT_FORBIDDEN", "普通字典不能配置父级"));
            }
            if (item.getParentId() != null && !byId.containsKey(item.getParentId())) {
                issues.add(new DictionaryModels.PublicationIssue(path + ".parentId", "DICTIONARY_PARENT_MISSING", "父级字典项不存在"));
            }
            Set<Long> visited = new HashSet<>();
            CfgDictionaryItem cursor = item;
            while (cursor.getParentId() != null) {
                if (!visited.add(cursor.getId())) {
                    issues.add(new DictionaryModels.PublicationIssue(path + ".parentId", "DICTIONARY_HIERARCHY_CYCLE", "字典层级不能成环"));
                    break;
                }
                cursor = byId.get(cursor.getParentId());
                if (cursor == null) break;
            }
        }
        return issues;
    }

    private CfgDictionaryItem validateParent(CfgDictionary dictionary, CfgDictionaryItem moving, Long parentId) {
        if (parentId == null) return null;
        if (!Boolean.TRUE.equals(dictionary.getHierarchical())) {
            throw new DomainException("FLAT_DICTIONARY_PARENT_FORBIDDEN", "普通字典不能配置父级", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        CfgDictionaryItem parent = requireItem(dictionary.getId(), parentId);
        if (moving != null) {
            if (moving.getId().equals(parentId) || parent.getPathCode().startsWith(moving.getPathCode() + ",")) {
                throw new DomainException("DICTIONARY_HIERARCHY_CYCLE", "字典项不能移动到自身或子级下", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        return parent;
    }

    private void rewriteDescendantPaths(Long dictionaryId, String oldPrefix, String newPrefix) {
        items(dictionaryId).stream()
                .filter(candidate -> candidate.getPathCode().startsWith(oldPrefix + ","))
                .sorted(Comparator.comparingInt(candidate -> candidate.getPathCode().length()))
                .forEach(candidate -> {
                    candidate.setPathCode(newPrefix + candidate.getPathCode().substring(oldPrefix.length()));
                    if (itemService.updateById(candidate) == 0) throw conflict("字典层级已被并发修改");
                });
    }

    private long itemUsage(Long dictionaryId, String itemCode) {
        Long result = jdbc.queryForObject("select count(*) from biz_record_value v "
                        + "join cfg_module_field f on f.id=v.field_id "
                        + "join biz_record r on r.id=v.record_id and r.deleted=0 "
                        + "where f.dictionary_id=? and (v.value_text=? or json_contains(v.value_json, json_quote(?), '$'))",
                Long.class, dictionaryId, itemCode, itemCode);
        return result == null ? 0 : result;
    }

    private List<CfgDictionaryItem> items(Long dictionaryId) {
        return itemService.selectList(Wrappers.<CfgDictionaryItem>lambdaQuery()
                .eq(CfgDictionaryItem::getDictionaryId, dictionaryId)
                .orderByAsc(CfgDictionaryItem::getPathCode, CfgDictionaryItem::getSortOrder, CfgDictionaryItem::getId));
    }

    private CfgDictionaryPublication publication(Long dictionaryId) {
        return publicationService.selectList(Wrappers.<CfgDictionaryPublication>lambdaQuery()
                        .eq(CfgDictionaryPublication::getDictionaryId, dictionaryId))
                .stream().findFirst().orElse(null);
    }

    private CfgDictionary requireOwned(AuthenticatedContext context, Long dictionaryId) {
        requireContext(context);
        CfgDictionary dictionary = dictionaryService.selectById(dictionaryId);
        if (dictionary == null || !context.systemId().equals(dictionary.getSystemId())
                || !context.tenantId().equals(dictionary.getOwnerTenantId())) {
            throw notFound("字典不存在");
        }
        return dictionary;
    }

    private CfgDictionaryItem requireItem(Long dictionaryId, Long itemId) {
        CfgDictionaryItem item = itemService.selectById(itemId);
        if (item == null || !dictionaryId.equals(item.getDictionaryId())) throw notFound("字典项不存在");
        return item;
    }

    private void touch(CfgDictionary dictionary) {
        if (dictionaryService.updateById(dictionary) == 0) throw conflict("字典草稿已被其他操作修改");
    }

    private void requireContext(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.CONFLICT);
        }
    }

    private String path(CfgDictionaryItem parent, String code) {
        return parent == null ? code : parent.getPathCode() + "," + code;
    }

    private String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize dictionary snapshot", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private DomainException notFound(String message) {
        return new DomainException("DICTIONARY_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }

    private DomainException conflict(String message) {
        return new DomainException("DICTIONARY_VERSION_CONFLICT", message, HttpStatus.CONFLICT);
    }
}
