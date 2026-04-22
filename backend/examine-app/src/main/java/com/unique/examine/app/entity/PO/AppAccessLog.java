package com.unique.examine.app.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <p>
 * 对外 API 访问日志
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_app_access_log")
@Schema(name = "AppAccessLog对象", description = "对外 API 访问日志")
public class AppAccessLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "访问日志ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "clientId（鉴权失败可为空）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long clientId;

    @Schema(description = "accessKey（鉴权失败也可记录）")
    private String accessKey;

    @Schema(description = "请求追踪ID（requestId）")
    private String requestId;

    @Schema(description = "HTTP method")
    private String method;

    @Schema(description = "请求路径（不含域名）")
    private String path;

    @Schema(description = "queryString（可选，截断）")
    private String queryString;

    @Schema(description = "客户端 IP")
    private String ip;

    @Schema(description = "User-Agent（可选）")
    private String ua;

    @Schema(description = "HTTP 状态码")
    private Integer statusCode;

    @Schema(description = "业务码（ApiResult.code，可选）")
    private Integer bizCode;

    @Schema(description = "耗时 ms（可选）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long costMs;

    @Schema(description = "错误信息（可选，截断）")
    private String errorMsg;

    @Schema(description = "创建人 platId（一般为空）")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId（一般为空）")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
