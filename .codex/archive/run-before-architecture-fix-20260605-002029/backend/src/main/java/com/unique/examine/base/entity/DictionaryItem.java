package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("dictionary_item")
@Schema(description = "dictionary_item 表实体")
public class DictionaryItem {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "dict_id")
    @TableField("dict_id")
    private Long dictId;

    @Schema(description = "item_label")
    @TableField("item_label")
    private String itemLabel;

    @Schema(description = "item_value")
    @TableField("item_value")
    private String itemValue;

    @Schema(description = "sort_order")
    @TableField("sort_order")
    private Integer sortOrder;

    @Schema(description = "status")
    @TableField("status")
    private String status;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
