import { computed } from 'vue';
const props = defineProps();
const text = computed(() => String(props.value || '-'));
const tagType = computed(() => {
    const value = text.value.toUpperCase();
    if (['ENABLED', 'PUBLISHED', 'SUCCESS', 'APPROVED', 'UP', 'OK'].includes(value))
        return 'success';
    if (['DISABLED', 'FAILED', 'REJECTED', 'DOWN', 'ERROR', 'DELETED'].includes(value))
        return 'danger';
    if (['RUNNING', 'SUBMITTED', 'PROCESSING'].includes(value))
        return 'warning';
    return 'info';
});
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
const __VLS_0 = {}.ElTag;
/** @type {[typeof __VLS_components.ElTag, typeof __VLS_components.elTag, typeof __VLS_components.ElTag, typeof __VLS_components.elTag, ]} */ ;
// @ts-ignore
const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
    type: (__VLS_ctx.tagType),
    effect: "plain",
}));
const __VLS_2 = __VLS_1({
    type: (__VLS_ctx.tagType),
    effect: "plain",
}, ...__VLS_functionalComponentArgsRest(__VLS_1));
var __VLS_4 = {};
__VLS_3.slots.default;
(__VLS_ctx.text);
var __VLS_3;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            text: text,
            tagType: tagType,
        };
    },
    __typeProps: {},
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
    __typeProps: {},
});
; /* PartiallyEnd: #4569/main.vue */
