<script setup lang="ts">
import { ConfigProvider } from 'ant-design-vue'

const theme = {
  token: {
    colorPrimary: '#076f65',
    colorInfo: '#2563a6',
    colorSuccess: '#21835a',
    colorWarning: '#b76700',
    colorError: '#bd3737',
    colorText: '#172033',
    colorTextSecondary: '#596576',
    colorBgLayout: '#f3f5f7',
    colorBgContainer: '#ffffff',
    colorBorder: '#d7dde3',
    colorBorderSecondary: '#e5e9ed',
    borderRadius: 8,
    borderRadiusLG: 12,
    fontSize: 14,
    controlHeight: 38,
    fontFamily: 'Inter, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
    boxShadowTertiary: '0 8px 24px rgb(31 48 68 / 8%)',
    zIndexPopupBase: 1600,
  },
  components: {
    Button: { primaryShadow: 'none', defaultShadow: 'none' },
    Table: { headerBg: '#f3f5f7', headerColor: '#435063', cellPaddingBlock: 11, cellPaddingInline: 12 },
  },
}
</script>

<template>
  <ConfigProvider :theme="theme">
    <RouterView />
  </ConfigProvider>
</template>
