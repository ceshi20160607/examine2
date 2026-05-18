import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import esbuild from 'esbuild'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
const pairs = [
  ['mobile/uniapp/src/api/module.ts', 'web/vue3/src/api/module.js'],
  ['mobile/uniapp/src/api/flow.ts', 'web/vue3/src/api/flow.js'],
  ['mobile/uniapp/src/api/meta.ts', 'web/vue3/src/api/meta.js'],
  ['mobile/uniapp/src/api/pages.ts', 'web/vue3/src/api/pages.js'],
  ['mobile/uniapp/src/api/records.ts', 'web/vue3/src/api/records.js'],
  ['mobile/uniapp/src/api/dept.ts', 'web/vue3/src/api/dept.js'],
  ['mobile/uniapp/src/api/flowBinding.ts', 'web/vue3/src/api/flowBinding.js'],
  ['mobile/uniapp/src/api/platformApp.ts', 'web/vue3/src/api/platformApp.js'],
  ['mobile/uniapp/src/api/rbac.ts', 'web/vue3/src/api/rbac.js'],
  ['mobile/uniapp/src/utils/fieldTypeEnum.ts', 'web/vue3/src/utils/fieldTypeEnum.js'],
  ['mobile/uniapp/src/utils/fieldTypes.ts', 'web/vue3/src/utils/fieldTypes.js'],
  ['mobile/uniapp/src/utils/refPicker.ts', 'web/vue3/src/utils/refPicker.js']
]

for (const [fromRel, toRel] of pairs) {
  const from = path.join(root, fromRel)
  const to = path.join(root, toRel)
  let src = fs.readFileSync(from, 'utf8')
  src = src.replace(/@\/api\//g, '../api/')
  src = src.replace(/@\/utils\//g, './')
  if (fromRel.includes('/utils/')) {
    src = src.replace(/from '\.\/records'/g, "from '../api/records.js'")
  }
  const { code } = await esbuild.transform(src, {
    loader: 'ts',
    format: 'esm',
    target: 'es2020'
  })
  fs.mkdirSync(path.dirname(to), { recursive: true })
  fs.writeFileSync(to, code)
  console.log('wrote', toRel)
}
