import {
  configFromMeta,
  defaultConfigFor,
  fieldTypeDef,
  fieldTypeOptionsForSelect,
  parseFieldTypeCode
} from "./fieldTypeEnum";
function fieldTypeCode(f) {
  return parseFieldTypeCode(f.fieldType);
}
function normalizeFieldType(t) {
  return parseFieldTypeCode(t);
}
function fieldTypeNeedsDict(fieldType) {
  return !!fieldTypeDef(parseFieldTypeCode(fieldType)).needsDict;
}
function fieldTypeNeedsRef(fieldType) {
  return !!fieldTypeDef(parseFieldTypeCode(fieldType)).needsRef;
}
function isRefField(f) {
  return fieldTypeCode(f) === "REF_MODULE";
}
function isRefMultiField(f) {
  if (!isRefField(f)) return false;
  const cfg = configFromMeta(f);
  if (cfg.displayStyle === "table" || cfg.subTable === true) return true;
  return (f.multiFlag ?? 0) === 1 || cfg.multi === true;
}
function isRefTableField(f) {
  if (!isRefField(f)) return false;
  const cfg = configFromMeta(f);
  return cfg.displayStyle === "table" || cfg.subTable === true;
}
function isAddressMapEnabled(f) {
  if (!isAddressField(f)) return false;
  const cfg = configFromMeta(f);
  return cfg.includeLocation === true || cfg.mapPicker === true;
}
function isNumberField(f) {
  const c = fieldTypeCode(f);
  return c === "NUMBER" || c === "MONEY" || c === "PERCENT";
}
function isDateField(f) {
  return fieldTypeCode(f) === "DATETIME";
}
function isDateRangeField(f) {
  return fieldTypeCode(f) === "DATE_RANGE";
}
function datePickerType(f) {
  const cfg = configFromMeta(f);
  const mode = String(cfg.pickerMode || "datetime");
  if (mode === "date") return "date";
  if (mode === "time") return "time";
  return "datetime";
}
function isTextareaField(f) {
  return fieldTypeCode(f) === "TEXTAREA";
}
function isRichTextField(f) {
  return fieldTypeCode(f) === "RICH_TEXT";
}
function isSignatureField(f) {
  return fieldTypeCode(f) === "SIGNATURE";
}
function isRatingSelectField(f) {
  if (fieldTypeCode(f) !== "SELECT") return false;
  return configFromMeta(f).displayStyle === "rating";
}
function isPasswordField(f) {
  if (fieldTypeCode(f) !== "TEXT") return false;
  return configFromMeta(f).inputStyle === "password";
}
function isJsonField(f) {
  return false;
}
function isBooleanField(f) {
  return fieldTypeCode(f) === "BOOLEAN";
}
function isFileField(f) {
  return fieldTypeCode(f) === "FILE";
}
function isImageField(f) {
  return false;
}
function isDictSingle(f) {
  if (fieldTypeCode(f) !== "SELECT" || !f.dictCode) return false;
  const style = String(configFromMeta(f).displayStyle || "dropdown");
  return style !== "rating" && style !== "radio";
}
function isRadioSelectField(f) {
  if (fieldTypeCode(f) !== "SELECT" || !f.dictCode) return false;
  return configFromMeta(f).displayStyle === "radio";
}
function isDictMulti(f) {
  return fieldTypeCode(f) === "MULTI_SELECT" && !!f.dictCode;
}
function isValidatedTextField(f) {
  if (fieldTypeCode(f) !== "TEXT") return false;
  const s = configFromMeta(f).inputStyle;
  return s === "email" || s === "phone" || s === "url";
}
function isPlainTextField(f) {
  return fieldTypeCode(f) === "TEXT" && !isPasswordField(f) && !isValidatedTextField(f);
}
function isTitleField(f) {
  return fieldTypeCode(f) === "TITLE";
}
function isAddressField(f) {
  return fieldTypeCode(f) === "ADDRESS";
}
function isTagField(f) {
  return fieldTypeCode(f) === "TAG";
}
function isPersonField(f) {
  return fieldTypeCode(f) === "PERSON";
}
function isDepartmentField(f) {
  return fieldTypeCode(f) === "DEPARTMENT";
}
function isSerialNoField(f) {
  return fieldTypeCode(f) === "SERIAL_NO";
}
function multiFlagForFieldType(fieldType, config) {
  const code = parseFieldTypeCode(fieldType);
  if (code === "MULTI_SELECT") return 1;
  if (code === "REF_MODULE" && config?.multi) return 1;
  if ((code === "PERSON" || code === "DEPARTMENT") && config?.multi) return 1;
  return 0;
}
function validateTypeForFieldType(fieldType, config) {
  if (parseFieldTypeCode(fieldType) !== "TEXT") return null;
  const style = String(config?.inputStyle || "normal");
  if (style === "email") return "email";
  if (style === "phone") return "phone";
  if (style === "url") return "url";
  if (style === "password") return "password";
  return null;
}
function storageFieldType(fieldType) {
  return parseFieldTypeCode(fieldType);
}
function uiFieldTypeFromMeta(f) {
  return parseFieldTypeCode(f.fieldType);
}
function inputTypeForField(f) {
  const style = configFromMeta(f).inputStyle;
  if (style === "password") return "password";
  if (style === "phone") return "number";
  return void 0;
}
function buildConfigJson(fieldType, config) {
  const c = { ...defaultConfigFor(parseFieldTypeCode(fieldType)), ...config };
  const keys = Object.keys(c).filter((k) => c[k] !== void 0 && c[k] !== "");
  if (keys.length === 0) return null;
  return JSON.stringify(c);
}
export {
  fieldTypeOptionsForSelect as FIELD_TYPE_OPTIONS,
  buildConfigJson,
  configFromMeta,
  datePickerType,
  defaultConfigFor,
  fieldTypeCode,
  fieldTypeNeedsDict,
  fieldTypeNeedsRef,
  inputTypeForField,
  isAddressField,
  isAddressMapEnabled,
  isBooleanField,
  isDateField,
  isDateRangeField,
  isDepartmentField,
  isDictMulti,
  isDictSingle,
  isFileField,
  isImageField,
  isJsonField,
  isNumberField,
  isPasswordField,
  isPersonField,
  isPlainTextField,
  isRadioSelectField,
  isRatingSelectField,
  isRefField,
  isRefMultiField,
  isRefTableField,
  isRichTextField,
  isSerialNoField,
  isSignatureField,
  isTagField,
  isTextareaField,
  isTitleField,
  isValidatedTextField,
  multiFlagForFieldType,
  normalizeFieldType,
  parseFieldTypeCode,
  storageFieldType,
  uiFieldTypeFromMeta,
  validateTypeForFieldType
};
