import assert from 'node:assert/strict'
import { afterEach, beforeEach, test } from 'node:test'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { validateProjectDocs } from './verify-project-docs.mjs'

const requiredFiles = [
  'AGENTS.md',
  'README.md',
  'docs/README.md',
  'docs/project/README.md',
  'docs/project/architecture.md',
  'docs/project/development.md',
  'docs/project/roadmap.md',
  'docs/project/status/overview.md',
  'docs/project/status/server.md',
  'docs/project/status/frontend.md',
  'docs/project/status/firmware.md',
  'docs/project/status/deployment.md',
  'docs/project/decisions/README.md',
  'docs/project/templates/handoff.md',
  'docs/project/templates/decision.md',
]

const validStatus = `# 工作流状态
- 状态：STABLE
- 最后更新：2026-07-19
- 当前分支：codex/stackchan-foundation
- 基准提交：f91861
- 最后验证提交：4880e12

## 当前目标
保持当前能力稳定。
## 已完成
现有能力已经验证。
## 正在进行
无正在进行的代码修改。
## 下一步操作
执行精确动作并运行记录的验证命令。
## 阻塞项
无。
## 关键文件
- README.md
## 验证命令与最近结果
- \`pnpm docs:check\`：通过。
## 相关设计、计划和决策
- docs/README.md
## 安全与兼容性约束
不得记录秘密。`

function createFixture() {
  const root = mkdtempSync(join(tmpdir(), 'stackchan-docs-'))
  for (const relativePath of requiredFiles) {
    const absolutePath = join(root, relativePath)
    mkdirSync(dirname(absolutePath), { recursive: true })
    const content = relativePath.includes('docs/project/status/') && !relativePath.endsWith('overview.md')
      ? validStatus
      : '# Document\n'
    writeFileSync(absolutePath, content, 'utf8')
  }
  writeFileSync(join(root, 'AGENTS.md'), '[状态](docs/project/status/overview.md)\n', 'utf8')
  writeFileSync(join(root, 'README.md'), '[项目文档](docs/project/README.md)\n', 'utf8')
  writeFileSync(join(root, 'docs/README.md'), '[项目文档](project/README.md)\n', 'utf8')
  writeFileSync(join(root, 'docs/project/README.md'), '[状态](status/overview.md)\n', 'utf8')
  return root
}

let root

beforeEach(() => {
  root = createFixture()
})

afterEach(() => {
  rmSync(root, { recursive: true, force: true })
})

test('accepts a complete documentation fixture', () => {
  assert.deepEqual(validateProjectDocs({ root, commitExists: () => true }), [])
})

test('reports a missing required file', () => {
  rmSync(join(root, 'AGENTS.md'))
  assert.match(validateProjectDocs({ root, commitExists: () => true }).join('\n'), /Missing required file: AGENTS.md/)
})

test('reports a missing status heading', () => {
  writeFileSync(join(root, 'docs/project/status/server.md'), validStatus.replace('## 下一步操作\n', ''))
  assert.match(validateProjectDocs({ root, commitExists: () => true }).join('\n'), /server.md: missing heading ## 下一步操作/)
})

test('reports vague placeholders in status files', () => {
  writeFileSync(join(root, 'docs/project/status/frontend.md'), validStatus.replace('执行精确动作', 'TBD'))
  assert.match(validateProjectDocs({ root, commitExists: () => true }).join('\n'), /frontend.md: contains vague placeholder/)
})

test('reports an unresolved verification commit', () => {
  assert.match(validateProjectDocs({ root, commitExists: () => false }).join('\n'), /verification commit does not exist/)
})

test('reports broken local markdown links', () => {
  writeFileSync(join(root, 'AGENTS.md'), '[缺失](docs/missing.md)')
  assert.match(validateProjectDocs({ root, commitExists: () => true }).join('\n'), /broken local link/)
})

test('reports active index rows without a workstream status link', () => {
  writeFileSync(join(root, 'docs/README.md'), '| 文档 | 状态 | 关联状态 |\n| --- | --- | --- |\n| 设计 | ACTIVE | 无 |\n')
  assert.match(validateProjectDocs({ root, commitExists: () => true }).join('\n'), /ACTIVE index row lacks workstream status link/)
})
