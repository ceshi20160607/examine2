import type { RuntimeMenuVO } from "../pages/runtime/runtimeWorkbenchPageModel";
import type { EntityId } from "../api";

export interface RuntimeModuleGroup {
    menuId: EntityId;
    name: string;
    code: string;
    modules: RuntimeMenuVO[];
}

/** 将 RUN-001 菜单树解析为「模块组 → 模块」结构 */
export function buildRuntimeModuleGroups(menus: RuntimeMenuVO[] = []): RuntimeModuleGroup[] {
    const groups: RuntimeModuleGroup[] = [];

    for (const menu of menus) {
        const childModules = (menu.children ?? []).filter((item) => item.moduleId);
        if (childModules.length > 0) {
            groups.push({
                menuId: menu.menuId,
                name: menu.name,
                code: menu.code,
                modules: childModules,
            });
            continue;
        }
        if (menu.moduleId) {
            let defaultGroup = groups.find((g) => g.code === "__default__");
            if (!defaultGroup) {
                defaultGroup = {
                    menuId: "default" as EntityId,
                    name: "业务模块",
                    code: "__default__",
                    modules: [],
                };
                groups.push(defaultGroup);
            }
            defaultGroup.modules.push(menu);
        }
    }

    return groups;
}

export function findRuntimeModuleGroup(
    groups: RuntimeModuleGroup[],
    groupMenuId?: EntityId,
): RuntimeModuleGroup | undefined {
    if (!groups.length) {
        return undefined;
    }
    if (groupMenuId) {
        return groups.find((g) => String(g.menuId) === String(groupMenuId)) ?? groups[0];
    }
    return groups[0];
}

export function findRuntimeModuleInGroup(group: RuntimeModuleGroup | undefined, moduleId?: EntityId): RuntimeMenuVO | undefined {
    if (!group?.modules.length) {
        return undefined;
    }
    if (moduleId) {
        return group.modules.find((m) => String(m.moduleId) === String(moduleId)) ?? group.modules[0];
    }
    return group.modules[0];
}

export const RUNTIME_SCENES = ["全部", "在用", "待年检", "维保中", "我负责的"] as const;

export type RuntimeScene = (typeof RUNTIME_SCENES)[number];
