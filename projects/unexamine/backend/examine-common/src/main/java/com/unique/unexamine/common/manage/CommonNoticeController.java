package com.unique.unexamine.common.manage;

import com.unique.unexamine.common.base.entity.UnCommonNotice;
import com.unique.unexamine.common.base.mapper.UnCommonNoticeMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

/** 公共公告的草稿、发布、撤销及范围内阅读入口。 */
@RestController
@RequestMapping("/api/common/notices")
public class CommonNoticeController {
    private final UnCommonNoticeMapper notices;
    private final ResourceScopeAccess scope;

    public CommonNoticeController(UnCommonNoticeMapper notices, ResourceScopeAccess scope) {
        this.notices = notices;
        this.scope = scope;
    }

    /** 普通成员只看到有效发布公告；管理员可显式查看草稿和撤销记录。 */
    @GetMapping
    public IPage<UnCommonNotice> list(@RequestParam String scopeType, @RequestParam long scopeId,
                                      @RequestParam(defaultValue = "false") boolean includeDrafts,
                                      @RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "20") long size,
                                      @RequestParam(defaultValue = "publishTime") String sort,
                                      HttpServletRequest request) {
        if (includeDrafts) scope.admin(scopeType, scopeId, request);
        else scope.read(scopeType, scopeId, request);
        if (page < 1 || size < 1 || size > 100) throw bad("分页参数无效");
        LambdaQueryWrapper<UnCommonNotice> query = new LambdaQueryWrapper<UnCommonNotice>()
                .eq(UnCommonNotice::getScopeType, scopeType).eq(UnCommonNotice::getScopeId, scopeId);
        if (!includeDrafts) query.eq(UnCommonNotice::getStatus, 1)
                .le(UnCommonNotice::getPublishTime, LocalDateTime.now())
                .and(q -> q.isNull(UnCommonNotice::getExpireTime)
                        .or().gt(UnCommonNotice::getExpireTime, LocalDateTime.now()));
        switch (sort) {
            case "title" -> query.orderByAsc(UnCommonNotice::getTitle);
            case "createTime" -> query.orderByDesc(UnCommonNotice::getCreateTime);
            case "publishTime" -> query.orderByDesc(UnCommonNotice::getPublishTime);
            default -> throw bad("不支持的公告排序字段");
        }
        return notices.selectPage(new Page<>(page, size), query);
    }

    /** 草稿和过期公告需要管理员权限才能查看。 */
    @GetMapping("/{id}")
    public UnCommonNotice detail(@PathVariable long id, HttpServletRequest request) {
        UnCommonNotice item = notice(id);
        if (visible(item)) scope.read(item.getScopeType(), item.getScopeId(), request);
        else scope.admin(item.getScopeType(), item.getScopeId(), request);
        return item;
    }

    /** 新公告先保存为草稿，发布操作单独执行。 */
    @PostMapping
    public UnCommonNotice create(@Valid @RequestBody NoticeInput input, HttpServletRequest request) {
        scope.admin(input.scopeType(), input.scopeId(), request);
        UnCommonNotice item = new UnCommonNotice();
        item.setScopeType(input.scopeType());
        item.setScopeId(input.scopeId());
        fill(item, input);
        item.setStatus((byte) 0);
        notices.insert(item);
        return item;
    }

    /** 只允许编辑尚未发布的公告内容，范围在创建后不可改变。 */
    @PutMapping("/{id}")
    public UnCommonNotice update(@PathVariable long id, @Valid @RequestBody NoticeContentInput input,
                                 HttpServletRequest request) {
        UnCommonNotice item = notice(id);
        scope.admin(item.getScopeType(), item.getScopeId(), request);
        if (item.getStatus() != 0) throw conflict("只能编辑草稿公告");
        item.setTitle(input.title().trim());
        item.setContent(input.content());
        item.setExpireTime(input.expireTime());
        notices.updateById(item);
        return item;
    }

    /** 发布时记录真实发布时间，过期时间必须晚于发布时间。 */
    @PostMapping("/{id}/publish")
    public UnCommonNotice publish(@PathVariable long id, HttpServletRequest request) {
        UnCommonNotice item = notice(id);
        Long publisherId = scope.admin(item.getScopeType(), item.getScopeId(), request);
        if (item.getStatus() != 0) throw conflict("公告不是草稿状态");
        LocalDateTime now = LocalDateTime.now();
        if (item.getExpireTime() != null && !item.getExpireTime().isAfter(now)) throw bad("公告过期时间无效");
        item.setStatus((byte) 1);
        // 系统级公告由平台管理员发布时，发布人不冒充系统成员。
        item.setPublisherId("system".equals(item.getScopeType()) ? null : publisherId);
        item.setPublishTime(now);
        notices.updateById(item);
        return item;
    }

    /** 撤销发布公告后普通成员不再能读取。 */
    @PostMapping("/{id}/revoke")
    public UnCommonNotice revoke(@PathVariable long id, HttpServletRequest request) {
        UnCommonNotice item = notice(id);
        scope.admin(item.getScopeType(), item.getScopeId(), request);
        if (item.getStatus() != 1) throw conflict("只能撤销已发布公告");
        item.setStatus((byte) 2);
        notices.updateById(item);
        return item;
    }

    private void fill(UnCommonNotice item, NoticeInput input) {
        item.setTitle(input.title().trim());
        item.setContent(input.content());
        item.setExpireTime(input.expireTime());
    }

    private boolean visible(UnCommonNotice item) {
        LocalDateTime now = LocalDateTime.now();
        return item.getStatus() == 1 && item.getPublishTime() != null
                && !item.getPublishTime().isAfter(now)
                && (item.getExpireTime() == null || item.getExpireTime().isAfter(now));
    }

    private UnCommonNotice notice(long id) {
        UnCommonNotice item = notices.selectById(id);
        if (item == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "公告不存在");
        return item;
    }

    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT, text); }

    /** 创建公告时范围由服务端校验，不允许客户端指定状态。 */
    public record NoticeInput(@NotBlank String scopeType, long scopeId,
                              @NotBlank @Size(max = 200) String title,
                              @NotBlank String content, LocalDateTime expireTime) {}
    public record NoticeContentInput(@NotBlank @Size(max = 200) String title,
                                     @NotBlank String content, LocalDateTime expireTime) {}
}
