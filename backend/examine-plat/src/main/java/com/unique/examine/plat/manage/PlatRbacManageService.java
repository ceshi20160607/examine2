package com.unique.examine.plat.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.security.PlatPermissionSupport;
import com.unique.examine.plat.entity.dto.PlatMenuTreeNode;
import com.unique.examine.plat.entity.po.PlatAccountRole;
import com.unique.examine.plat.entity.po.PlatMenu;
import com.unique.examine.plat.service.IPlatAccountRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 平台 RBAC 编排（手写业务；与 {@code com.unique.examine.plat.service} 下模板 CRUD 区分）。
 */
@Service
public class PlatRbacManageService {

    private static final long ROLE_PLAT_SUPER_ADMIN = 1L;
    private static final long ROLE_PLAT_USER = 2L;

    @Autowired
    private IPlatAccountRoleService platAccountRoleService;

    public void bindDefaultRoleOnRegister(Long platAccountId, boolean firstAccount) {
        PlatAccountRole bind = new PlatAccountRole();
        bind.setPlatAccountId(platAccountId);
        bind.setRoleId(firstAccount ? ROLE_PLAT_SUPER_ADMIN : ROLE_PLAT_USER);
        bind.setCreateUserId(platAccountId);
        bind.setUpdateUserId(platAccountId);
        platAccountRoleService.save(bind);
    }

    public boolean hasAccountRoleBinding(Long platAccountId) {
        if (platAccountId == null) {
            return false;
        }
        return platAccountRoleService.count(new LambdaQueryWrapper<PlatAccountRole>()
                .eq(PlatAccountRole::getPlatAccountId, platAccountId)) > 0;
    }

    public Set<String> resolveEffectivePermCodes(Long platAccountId, String legacyPlatPermCodes) {
        if (platAccountId == null) {
            return Set.of();
        }
        if (hasAccountRoleBinding(platAccountId)) {
            List<String> rows = platAccountRoleService.listRbacPermCodes(platAccountId);
            LinkedHashSet<String> set = new LinkedHashSet<>();
            if (rows != null) {
                for (String c : rows) {
                    if (c != null) {
                        String t = c.trim();
                        if (!t.isEmpty()) {
                            set.add(t);
                        }
                    }
                }
            }
            return set;
        }
        return new LinkedHashSet<>(PlatPermissionSupport.parseCodes(legacyPlatPermCodes));
    }

    public List<String> listRoleCodes(Long platAccountId) {
        if (platAccountId == null || !hasAccountRoleBinding(platAccountId)) {
            return List.of();
        }
        List<String> list = platAccountRoleService.listRbacRoleCodes(platAccountId);
        return list == null ? List.of() : List.copyOf(list);
    }

    public List<PlatMenuTreeNode> buildMenuTree(Long platAccountId) {
        if (platAccountId == null || !hasAccountRoleBinding(platAccountId)) {
            return List.of();
        }
        List<PlatMenu> flat = platAccountRoleService.listRbacMenusByPlatAccount(platAccountId);
        if (flat == null || flat.isEmpty()) {
            return List.of();
        }
        Map<Long, PlatMenuTreeNode> byId = flat.stream()
                .map(this::toNode)
                .collect(Collectors.toMap(PlatMenuTreeNode::getId, Function.identity(), (a, b) -> a));
        List<PlatMenuTreeNode> roots = new ArrayList<>();
        for (PlatMenu m : flat) {
            PlatMenuTreeNode n = byId.get(m.getId());
            if (n == null) {
                continue;
            }
            Long pid = m.getParentId();
            if (pid == null || pid == 0L) {
                roots.add(n);
            } else {
                PlatMenuTreeNode p = byId.get(pid);
                if (p != null) {
                    p.getChildren().add(n);
                } else {
                    roots.add(n);
                }
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<PlatMenuTreeNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator
                .comparing(PlatMenuTreeNode::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(PlatMenuTreeNode::getId, Comparator.nullsLast(Long::compareTo)));
        for (PlatMenuTreeNode n : nodes) {
            sortTree(n.getChildren());
        }
    }

    private PlatMenuTreeNode toNode(PlatMenu m) {
        PlatMenuTreeNode n = new PlatMenuTreeNode();
        n.setId(m.getId());
        n.setParentId(m.getParentId());
        n.setMenuName(m.getMenuName());
        n.setMenuType(m.getMenuType());
        n.setPath(m.getPath());
        n.setPermCode(m.getPermCode());
        n.setIcon(m.getIcon());
        n.setSortNo(m.getSortNo());
        n.setVisibleFlag(m.getVisibleFlag());
        n.setChildren(new ArrayList<>());
        return n;
    }
}
