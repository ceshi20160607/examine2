import type { FilterOperator, FilterRule, JsonValue, SortRule } from "../../api";
import { getDynamicFieldRenderer } from "./renderers";
import { normalizeListSchema } from "./normalizers";
import type { DynamicQueryDraft, DynamicSchemaInput } from "./types";

export function buildDynamicListQuery(input: DynamicSchemaInput, draft: DynamicQueryDraft = {}): DynamicQueryDraft {
  const listSchema = normalizeListSchema(input);
  const fieldByCode = new Map(input.fieldDefinitions.map((field) => [field.fieldCode, field]));
  const allowedFilters = new Set(
    (listSchema.filters ?? []).filter((item) => item.filterable !== false).map((item) => item.fieldCode),
  );
  const sortableRefs =
    listSchema.sorters && listSchema.sorters.length > 0
      ? listSchema.sorters.map((item) => item.fieldCode)
      : listSchema.columns.filter((item) => item.sortable !== false).map((item) => item.fieldCode);
  const allowedSorters = new Set(sortableRefs);

  return {
    keyword: draft.keyword,
    filters: normalizeFilters(draft.filters ?? [], allowedFilters, fieldByCode),
    sorter: normalizeSorters(draft.sorter ?? [], allowedSorters, fieldByCode),
  };
}

function normalizeFilters(
  filters: Array<FilterRule<JsonValue>>,
  allowedFilters: Set<string>,
  fields: Map<string, { fieldType: Parameters<typeof getDynamicFieldRenderer>[0] }>,
): Array<FilterRule<JsonValue>> {
  return filters.filter((filter) => {
    const field = fields.get(filter.field);
    if (!field || !allowedFilters.has(filter.field)) {
      return false;
    }
    const renderer = getDynamicFieldRenderer(field.fieldType);
    return renderer.filterable && operatorAllowed(filter.op, renderer.defaultOperators);
  });
}

function normalizeSorters(
  sorters: SortRule[],
  allowedSorters: Set<string>,
  fields: Map<string, { fieldType: Parameters<typeof getDynamicFieldRenderer>[0] }>,
): SortRule[] {
  return sorters.filter((sorter) => {
    const field = fields.get(sorter.field);
    return Boolean(field && allowedSorters.has(sorter.field) && getDynamicFieldRenderer(field.fieldType).sortable);
  });
}

function operatorAllowed(operator: FilterOperator, allowed: FilterOperator[]): boolean {
  return allowed.includes(operator);
}
