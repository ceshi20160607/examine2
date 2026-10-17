package com.unique.unexamine.system.manage;

/** 受让人搜索结果仅包含选择时需要的平台账号基础信息。 */
public record SystemOwnerCandidateView(Long accountId, String username, String displayName) {
}
