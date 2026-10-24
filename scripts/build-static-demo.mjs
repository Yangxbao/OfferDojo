// 静态演示版构建脚本（跨平台，不引入新依赖）：
// 通过环境变量 BUILD_MODE=static-demo 触发 vite.config.ts 中的 define 注入。
// 产物输出到 frontend/dist（与正式构建一致，上传 OSS 的是同一目录形态）。
import { spawn } from 'node:child_process'

const env = { ...process.env, BUILD_MODE: 'static-demo' }
const npmCmd = process.platform === 'win32' ? 'npm.cmd' : 'npm'

const child = spawn(npmCmd, ['run', 'build'], {
  cwd: new URL('../frontend', import.meta.url).pathname.replace(/^\/([A-Z]:)/, '$1'),
  env,
  stdio: 'inherit',
  shell: true,
})

child.on('close', (code) => {
  if (code === 0) {
    console.log('\n✅ 静态演示版构建完成 → frontend/dist（可直接上传 OSS Bucket 根目录）')
    console.log('   提示：上传 index.html 与 assets/ 目录；OSS 需配置默认首页与 404 错误页均为 index.html')
  }
  process.exit(code ?? 1)
})
