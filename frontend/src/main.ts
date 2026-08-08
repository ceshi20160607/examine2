import 'ant-design-vue/dist/reset.css'
import './styles/base.css'

import {
  Alert,
  Badge,
  Button,
  Cascader,
  Checkbox,
  Descriptions,
  Divider,
  Drawer,
  Dropdown,
  Empty,
  Form,
  Input,
  InputNumber,
  Menu,
  Modal,
  Pagination,
  Popconfirm,
  Rate,
  Segmented,
  Select,
  Slider,
  Spin,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
} from 'ant-design-vue'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import router from './router'

createApp(App)
  .use(createPinia())
  .use(router)
  .use(Alert)
  .use(Badge)
  .use(Button)
  .use(Cascader)
  .use(Dropdown)
  .use(Form)
  .use(Input)
  .use(InputNumber)
  .use(Menu)
  .use(Checkbox)
  .use(Descriptions)
  .use(Divider)
  .use(Drawer)
  .use(Empty)
  .use(Modal)
  .use(Pagination)
  .use(Popconfirm)
  .use(Rate)
  .use(Segmented)
  .use(Select)
  .use(Slider)
  .use(Spin)
  .use(Switch)
  .use(Table)
  .use(Tabs)
  .use(Tag)
  .use(Tooltip)
  .mount('#app')
