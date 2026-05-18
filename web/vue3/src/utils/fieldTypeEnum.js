const MODULE_FIELD_TYPES = [
  { code: "TEXT", label: "\u5355\u884C\u6587\u672C" },
  { code: "TEXTAREA", label: "\u591A\u884C\u6587\u672C" },
  { code: "NUMBER", label: "\u6570\u5B57" },
  { code: "MONEY", label: "\u91D1\u989D" },
  { code: "PERCENT", label: "\u767E\u5206\u6BD4" },
  { code: "DATETIME", label: "\u65E5\u671F\u65F6\u95F4" },
  { code: "BOOLEAN", label: "\u5E03\u5C14" },
  { code: "SELECT", label: "\u5355\u9009", needsDict: true },
  { code: "MULTI_SELECT", label: "\u591A\u9009", needsDict: true, allowsMulti: true },
  { code: "ADDRESS", label: "\u5730\u5740" },
  { code: "PERSON", label: "\u4EBA\u5458", allowsMulti: true },
  { code: "DEPARTMENT", label: "\u90E8\u95E8", allowsMulti: true },
  { code: "TITLE", label: "\u5360\u4F4D\u6807\u9898", displayOnly: true },
  { code: "SIGNATURE", label: "\u624B\u5199\u7B7E\u540D" },
  { code: "SERIAL_NO", label: "\u81EA\u5B9A\u4E49\u7F16\u53F7" },
  { code: "RICH_TEXT", label: "\u5BCC\u6587\u672C" },
  { code: "REF_MODULE", label: "\u5173\u8054\u6A21\u5757", needsRef: true, allowsMulti: true },
  { code: "TAG", label: "\u6807\u7B7E" },
  { code: "DATE_RANGE", label: "\u65E5\u671F\u533A\u95F4" },
  { code: "FILE", label: "\u9644\u4EF6" }
];
const LEGACY_MAP = {
  text: "TEXT",
  string: "TEXT",
  password: "TEXT",
  email: "TEXT",
  phone: "TEXT",
  url: "TEXT",
  textarea: "TEXTAREA",
  number: "NUMBER",
  integer: "NUMBER",
  decimal: "NUMBER",
  money: "MONEY",
  currency: "MONEY",
  percent: "PERCENT",
  date: "DATETIME",
  datetime: "DATETIME",
  time: "DATETIME",
  boolean: "BOOLEAN",
  bool: "BOOLEAN",
  switch: "BOOLEAN",
  select: "SELECT",
  multiselect: "MULTI_SELECT",
  radio: "SELECT",
  checkbox: "MULTI_SELECT",
  address: "ADDRESS",
  region: "ADDRESS",
  person: "PERSON",
  user: "PERSON",
  department: "DEPARTMENT",
  dept: "DEPARTMENT",
  title: "TITLE",
  signature: "SIGNATURE",
  sign: "SIGNATURE",
  serial_no: "SERIAL_NO",
  serial: "SERIAL_NO",
  rich_text: "RICH_TEXT",
  richtext: "RICH_TEXT",
  ref: "REF_MODULE",
  relation: "REF_MODULE",
  ref_multi: "REF_MODULE",
  tag: "TAG",
  tags: "TAG",
  date_range: "DATE_RANGE",
  file: "FILE",
  image: "FILE",
  attachment: "FILE"
};
function parseFieldTypeCode(raw) {
  if (!raw) return "TEXT";
  const u = String(raw).trim().toUpperCase();
  const hit = MODULE_FIELD_TYPES.find((t) => t.code === u);
  if (hit) return hit.code;
  return LEGACY_MAP[String(raw).trim().toLowerCase()] || "TEXT";
}
function fieldTypeDef(code) {
  return MODULE_FIELD_TYPES.find((t) => t.code === code) || MODULE_FIELD_TYPES[0];
}
function fieldTypeOptionsForSelect() {
  return MODULE_FIELD_TYPES.map((t) => ({ value: t.code, text: t.label }));
}
function defaultConfigFor(code) {
  switch (code) {
    case "TEXT":
      return { inputStyle: "normal", maxLength: 200 };
    case "MONEY":
      return { decimalPlaces: 2 };
    case "PERCENT":
      return { decimalPlaces: 0 };
    case "DATETIME":
      return { pickerMode: "datetime" };
    case "SELECT":
      return { displayStyle: "dropdown" };
    case "MULTI_SELECT":
      return { displayStyle: "dropdown" };
    case "ADDRESS":
      return { regionStyle: "cascade", includeLocation: false, detailMode: "manual", mapPicker: false };
    case "PERSON":
    case "DEPARTMENT":
      return { scope: "system", multi: false };
    case "TITLE":
      return { content: "" };
    case "REF_MODULE":
      return { displayStyle: "inline", listFields: [], multi: true, subTable: false };
    case "TAG":
      return { tags: [], allowCustom: true };
    case "SERIAL_NO":
      return { segments: [{ type: "seq", width: 4, reset: "never" }] };
    case "DATE_RANGE":
      return { pickerMode: "date" };
    default:
      return {};
  }
}
function configFromMeta(f) {
  const code = parseFieldTypeCode(f.fieldType);
  let cfg = {};
  if (f.configJson) {
    try {
      cfg = JSON.parse(f.configJson);
    } catch {
      cfg = {};
    }
  }
  if (code === "TEXT" && f.validateType) {
    const vt = String(f.validateType).toLowerCase();
    const style = vt === "password" ? "password" : vt === "phone" ? "phone" : vt === "email" ? "email" : vt === "url" ? "url" : "normal";
    cfg = { ...defaultConfigFor("TEXT"), ...cfg, inputStyle: cfg.inputStyle || style };
  }
  if (code === "REF_MODULE" && (f.multiFlag ?? 0) === 1) {
    cfg = { ...cfg, multi: true };
  }
  return { ...defaultConfigFor(code), ...cfg };
}
export {
  MODULE_FIELD_TYPES,
  configFromMeta,
  defaultConfigFor,
  fieldTypeDef,
  fieldTypeOptionsForSelect,
  parseFieldTypeCode
};
