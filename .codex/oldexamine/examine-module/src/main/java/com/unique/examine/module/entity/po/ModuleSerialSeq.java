package com.unique.examine.module.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_serial_seq")
public class ModuleSerialSeq implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    private String fieldCode;

    private String resetKey;

    private Long seqNo;

    private LocalDateTime updateTime;
}
