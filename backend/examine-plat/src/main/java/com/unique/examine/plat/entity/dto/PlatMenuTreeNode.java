package com.unique.examine.plat.entity.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Schema(description = "平台菜单树节点")
public class PlatMenuTreeNode {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    private String menuName;
    private Integer menuType;
    private String path;
    private String permCode;
    private String icon;
    private Integer sortNo;
    private Integer visibleFlag;

    private List<PlatMenuTreeNode> children = new ArrayList<>();
}
