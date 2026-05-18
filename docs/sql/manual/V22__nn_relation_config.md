# n-n 关系运行时配置说明

`rel_type = n-n` 时，`un_module_relation.config_json` 示例：

```json
{
  "linkModelId": 100,
  "srcFkField": "orderId",
  "dstFkField": "productId"
}
```

- **linkModelId**：中间关联模型（存两边外键的一条记录模型）
- **srcFkField**：中间表指向**源模型父记录**的字段编码（EAV `field_code`）
- **dstFkField**：中间表指向**目标模型记录**的字段编码

查询：`POST /v1/system/records/query-by-relation`  
body: `{ "relationId", "parentRecordId", "query": { "page", "limit", "filters" } }`
