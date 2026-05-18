# n-n 关系运行时配置示例

`un_module_relation.rel_type = 'n-n'` 时，`config_json` 须为中间表（link 模型）方案：

```json
{
  "linkModelId": 100,
  "srcFkField": "orderId",
  "dstFkField": "productId"
}
```

- `linkModelId`：关联表对应业务模型 ID（该模型记录一行表示一条 n-n 链接）
- `srcFkField`：link 模型上指向**源侧父记录**的字段编码（EAV `field_code`）
- `dstFkField`：link 模型上指向**目标侧记录**的字段编码

查询接口：`POST /v1/system/records/query-by-relation`，body 含 `relationId`、`parentRecordId`、可选 `query`（分页/排序）。

`1-n` / `1-1` 仍使用 `{"fkField":"parentId"}`。

子表字段（REF 列表/子表样式）可在 `config_json` 中指定关系（编辑父记录时自动 `query-by-relation` 加载子行）：

```json
{
  "displayStyle": "table",
  "listFields": ["lineName", "qty"],
  "relationId": 1234567890123456789
}
```

未配置 `relationId` 时，若存在 **源模型=当前父模型、目标模型=refModelId、类型 1-n/1-1** 的关系，将自动匹配。
