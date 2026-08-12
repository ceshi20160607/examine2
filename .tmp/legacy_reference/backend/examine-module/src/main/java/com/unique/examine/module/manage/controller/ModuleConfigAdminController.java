package com.unique.examine.module.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.WebRequestAttributes;
import com.unique.examine.module.manage.api.ConfigRequests;
import com.unique.examine.module.manage.api.ConfigViews;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.manage.service.ConfigDraftService;
import com.unique.examine.module.manage.service.ConfigCheckService;
import com.unique.examine.module.manage.service.ConfigPublicationService;
import com.unique.examine.module.manage.service.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin/config")
public class ModuleConfigAdminController {
    private final ConfigDraftService service;
    private final ConfigCheckService checks;
    private final ConfigPublicationService publications;

    public ModuleConfigAdminController(ConfigDraftService service, ConfigCheckService checks,
                                       ConfigPublicationService publications) {
        this.service = service;
        this.checks = checks;
        this.publications = publications;
    }

    @GetMapping
    public ApiResponse<ConfigViews.RootSummary> root(
            @PathVariable long systemId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(service.root(systemId), request);
    }

    @PostMapping("/checks")
    public ApiResponse<ConfigViews.CheckReport> runCheck(
            @PathVariable long systemId,
            @Valid @RequestBody ConfigRequests.RunCheck body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(checks.run(session(value, systemId), body), request);
    }

    @GetMapping("/checks/{checkId}")
    public ApiResponse<ConfigViews.CheckReport> check(
            @PathVariable long systemId, @PathVariable long checkId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(checks.get(systemId, checkId), request);
    }

    @GetMapping("/versions")
    public ApiResponse<List<ConfigViews.ConfigVersion>> versions(
            @PathVariable long systemId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(publications.versions(systemId), request);
    }

    @GetMapping("/versions/{fromId}:diff/{toId}")
    public ApiResponse<ConfigViews.VersionDiff> diff(
            @PathVariable long systemId, @PathVariable long fromId, @PathVariable long toId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(publications.diff(systemId, fromId, toId), request);
    }

    @PostMapping("/versions/{versionId}:rollback")
    public ApiResponse<ConfigViews.PublishResult> rollback(
            @PathVariable long systemId, @PathVariable long versionId,
            @Valid @RequestBody ConfigRequests.RollbackConfig body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(publications.rollback(session(value, systemId), versionId, body, key, context(request)), request);
    }

    @GetMapping("/module-groups")
    public ApiResponse<List<ConfigViews.Group>> groups(
            @PathVariable long systemId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(service.groups(systemId), request);
    }

    @PostMapping("/module-groups")
    public ApiResponse<ConfigViews.Group> createGroup(
            @PathVariable long systemId,
            @Valid @RequestBody ConfigRequests.CreateGroup body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.createGroup(session(value, systemId), body, key, context(request)), request);
    }

    @PutMapping("/module-groups/{id}")
    public ApiResponse<ConfigViews.Group> updateGroup(
            @PathVariable long systemId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.UpdateGroup body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.updateGroup(session(value, systemId), id, body, context(request)), request);
    }

    @PostMapping("/module-groups/{id}:delete")
    public ApiResponse<ConfigViews.RevisionResult> deleteGroup(
            @PathVariable long systemId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.DeleteResource body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.deleteGroup(session(value, systemId), id, body, context(request)), request);
    }

    @GetMapping("/modules")
    public ApiResponse<List<ConfigViews.Module>> modules(
            @PathVariable long systemId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(service.modules(systemId), request);
    }

    @PostMapping("/modules")
    public ApiResponse<ConfigViews.Module> createModule(
            @PathVariable long systemId,
            @Valid @RequestBody ConfigRequests.CreateModule body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.createModule(session(value, systemId), body, key, context(request)), request);
    }

    @PutMapping("/modules/{id}")
    public ApiResponse<ConfigViews.Module> updateModule(
            @PathVariable long systemId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.UpdateModule body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.updateModule(session(value, systemId), id, body, context(request)), request);
    }

    @PostMapping("/modules/{id}:delete")
    public ApiResponse<ConfigViews.RevisionResult> deleteModule(
            @PathVariable long systemId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.DeleteResource body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.deleteModule(session(value, systemId), id, body, context(request)), request);
    }

    @GetMapping("/dictionaries")
    public ApiResponse<List<ConfigViews.Dictionary>> dictionaries(
            @PathVariable long systemId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(service.dictionaries(systemId), request);
    }

    @PostMapping("/dictionaries")
    public ApiResponse<ConfigViews.Dictionary> createDictionary(
            @PathVariable long systemId,
            @Valid @RequestBody ConfigRequests.CreateDictionary body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.createDictionary(session(value, systemId), body, key, context(request)), request);
    }

    @PutMapping("/dictionaries/{id}")
    public ApiResponse<ConfigViews.Dictionary> updateDictionary(
            @PathVariable long systemId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.UpdateDictionary body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.updateDictionary(session(value, systemId), id, body, context(request)), request);
    }

    @PostMapping("/dictionaries/{id}:delete")
    public ApiResponse<ConfigViews.RevisionResult> deleteDictionary(
            @PathVariable long systemId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.DeleteResource body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.deleteDictionary(session(value, systemId), id, body, context(request)), request);
    }

    @GetMapping("/dictionaries/{dictionaryId}/items")
    public ApiResponse<List<ConfigViews.DictionaryItem>> dictionaryItems(
            @PathVariable long systemId, @PathVariable long dictionaryId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(service.dictionaryItems(systemId, dictionaryId), request);
    }

    @PostMapping("/dictionaries/{dictionaryId}/items")
    public ApiResponse<ConfigViews.DictionaryItem> createDictionaryItem(
            @PathVariable long systemId, @PathVariable long dictionaryId,
            @Valid @RequestBody ConfigRequests.CreateDictionaryItem body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.createItem(session(value, systemId), dictionaryId, body, key, context(request)), request);
    }

    @PutMapping("/dictionaries/{dictionaryId}/items/{id}")
    public ApiResponse<ConfigViews.DictionaryItem> updateDictionaryItem(
            @PathVariable long systemId, @PathVariable long dictionaryId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.UpdateDictionaryItem body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.updateItem(session(value, systemId), dictionaryId, id, body, context(request)), request);
    }

    @PostMapping("/dictionaries/{dictionaryId}/items/{id}:delete")
    public ApiResponse<ConfigViews.RevisionResult> deleteDictionaryItem(
            @PathVariable long systemId, @PathVariable long dictionaryId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.DeleteResource body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.deleteItem(session(value, systemId), dictionaryId, id, body, context(request)), request);
    }

    @GetMapping("/modules/{moduleId}/fields")
    public ApiResponse<List<ConfigViews.Field>> fields(
            @PathVariable long systemId, @PathVariable long moduleId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(service.fields(systemId, moduleId), request);
    }

    @PostMapping("/modules/{moduleId}/fields")
    public ApiResponse<ConfigViews.Field> createField(
            @PathVariable long systemId, @PathVariable long moduleId,
            @Valid @RequestBody ConfigRequests.CreateField body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.createField(session(value, systemId), moduleId, body, key, context(request)), request);
    }

    @PutMapping("/modules/{moduleId}/fields/{id}")
    public ApiResponse<ConfigViews.Field> updateField(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.UpdateField body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.updateField(session(value, systemId), moduleId, id, body, context(request)), request);
    }

    @PostMapping("/modules/{moduleId}/fields/{id}:delete")
    public ApiResponse<ConfigViews.RevisionResult> deleteField(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.DeleteResource body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.deleteField(session(value, systemId), moduleId, id, body, context(request)), request);
    }

    @GetMapping("/modules/{moduleId}/pages")
    public ApiResponse<List<ConfigViews.Page>> pages(
            @PathVariable long systemId, @PathVariable long moduleId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(service.pages(systemId, moduleId), request);
    }

    @PostMapping("/modules/{moduleId}/pages")
    public ApiResponse<ConfigViews.Page> createPage(
            @PathVariable long systemId, @PathVariable long moduleId,
            @Valid @RequestBody ConfigRequests.CreatePage body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.createPage(session(value, systemId), moduleId, body, key, context(request)), request);
    }

    @PutMapping("/modules/{moduleId}/pages/{id}")
    public ApiResponse<ConfigViews.Page> updatePage(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.UpdatePage body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.updatePage(session(value, systemId), moduleId, id, body, context(request)), request);
    }

    @PostMapping("/modules/{moduleId}/pages/{id}:delete")
    public ApiResponse<ConfigViews.RevisionResult> deletePage(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.DeleteResource body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.deletePage(session(value, systemId), moduleId, id, body, context(request)), request);
    }

    @GetMapping("/modules/{moduleId}/pages/{pageId}/components")
    public ApiResponse<List<ConfigViews.Component>> components(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long pageId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(service.components(systemId, moduleId, pageId), request);
    }

    @PostMapping("/modules/{moduleId}/pages/{pageId}/components")
    public ApiResponse<ConfigViews.Component> createComponent(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long pageId,
            @Valid @RequestBody ConfigRequests.CreateComponent body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.createComponent(session(value, systemId), moduleId, pageId, body, key, context(request)), request);
    }

    @PutMapping("/modules/{moduleId}/pages/{pageId}/components/{id}")
    public ApiResponse<ConfigViews.Component> updateComponent(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long pageId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.UpdateComponent body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.updateComponent(session(value, systemId), moduleId, pageId, id, body, context(request)), request);
    }

    @PostMapping("/modules/{moduleId}/pages/{pageId}/components/{id}:delete")
    public ApiResponse<ConfigViews.RevisionResult> deleteComponent(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long pageId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.DeleteResource body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.deleteComponent(session(value, systemId), moduleId, pageId, id, body, context(request)), request);
    }

    @GetMapping("/modules/{moduleId}/actions")
    public ApiResponse<List<ConfigViews.Action>> actions(
            @PathVariable long systemId, @PathVariable long moduleId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(service.actions(systemId, moduleId), request);
    }

    @PostMapping("/modules/{moduleId}/actions")
    public ApiResponse<ConfigViews.Action> createAction(
            @PathVariable long systemId, @PathVariable long moduleId,
            @Valid @RequestBody ConfigRequests.CreateAction body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.createAction(session(value, systemId), moduleId, body, key, context(request)), request);
    }

    @PutMapping("/modules/{moduleId}/actions/{id}")
    public ApiResponse<ConfigViews.Action> updateAction(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.UpdateAction body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.updateAction(session(value, systemId), moduleId, id, body, context(request)), request);
    }

    @PostMapping("/modules/{moduleId}/actions/{id}:delete")
    public ApiResponse<ConfigViews.RevisionResult> deleteAction(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.DeleteResource body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.deleteAction(session(value, systemId), moduleId, id, body, context(request)), request);
    }

    @GetMapping("/modules/{moduleId}/rules")
    public ApiResponse<List<ConfigViews.Rule>> rules(
            @PathVariable long systemId, @PathVariable long moduleId,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        session(value, systemId);
        return ok(service.rules(systemId, moduleId), request);
    }

    @PostMapping("/modules/{moduleId}/rules")
    public ApiResponse<ConfigViews.Rule> createRule(
            @PathVariable long systemId, @PathVariable long moduleId,
            @Valid @RequestBody ConfigRequests.CreateRule body,
            @RequestHeader(name = "Idempotency-Key", required = false) String key,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.createRule(session(value, systemId), moduleId, body, key, context(request)), request);
    }

    @PutMapping("/modules/{moduleId}/rules/{id}")
    public ApiResponse<ConfigViews.Rule> updateRule(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.UpdateRule body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.updateRule(session(value, systemId), moduleId, id, body, context(request)), request);
    }

    @PostMapping("/modules/{moduleId}/rules/{id}:delete")
    public ApiResponse<ConfigViews.RevisionResult> deleteRule(
            @PathVariable long systemId, @PathVariable long moduleId, @PathVariable long id,
            @Valid @RequestBody ConfigRequests.DeleteResource body,
            @RequestAttribute(value = ConfigSession.REQUEST_ATTRIBUTE, required = false) Object value,
            HttpServletRequest request
    ) {
        return ok(service.deleteRule(session(value, systemId), moduleId, id, body, context(request)), request);
    }

    private static ConfigSession session(Object value, long systemId) {
        return ConfigSession.require(value, systemId);
    }

    private static RequestContext context(HttpServletRequest request) {
        return new RequestContext(attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }

    private static String attribute(HttpServletRequest request, String name) {
        return String.valueOf(request.getAttribute(name));
    }

    private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
        return ApiResponse.success(data, attribute(request, WebRequestAttributes.REQUEST_ID),
                attribute(request, WebRequestAttributes.TRACE_ID));
    }
}
