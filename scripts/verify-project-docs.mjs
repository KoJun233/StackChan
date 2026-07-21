import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const REQUIRED_FILES = [
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

const REQUIRED_METADATA = [
  '- 状态：',
  '- 最后更新：',
  '- 当前分支：',
  '- 基准提交：',
  '- 最后验证提交：',
]

const REQUIRED_HEADINGS = [
  '## 当前目标',
  '## 已完成',
  '## 正在进行',
  '## 下一步操作',
  '## 阻塞项',
  '## 关键文件',
  '## 验证命令与最近结果',
  '## 相关设计、计划和决策',
  '## 安全与兼容性约束',
]

const STATUS_FILES = [
  'docs/project/status/server.md',
  'docs/project/status/frontend.md',
  'docs/project/status/firmware.md',
  'docs/project/status/deployment.md',
]

const LINK_FILES = [
  'AGENTS.md',
  'README.md',
  'docs/README.md',
  'docs/project/README.md',
]

const VAGUE_PATTERN = /\b(?:TODO|TBD)\b|待定|稍后补充|继续优化|继续完善/

function defaultCommitExists(root, commit) {
  if (commit === 'ROOT') {
    try {
      execFileSync('git', ['rev-parse', '--verify', 'HEAD^'], {
        cwd: root,
        stdio: 'ignore',
      })
      return false
    }
    catch {
      return true
    }
  }
  try {
    execFileSync('git', ['cat-file', '-e', `${commit}^{commit}`], {
      cwd: root,
      stdio: 'ignore',
    })
    return true
  }
  catch {
    return false
  }
}

function validateLinks(root, relativePath, errors) {
  const absolutePath = resolve(root, relativePath)
  if (!existsSync(absolutePath)) {
    return
  }
  const text = readFileSync(absolutePath, 'utf8')
  for (const match of text.matchAll(/\[[^\]]+\]\(([^)]+)\)/g)) {
    const rawTarget = match[1].trim()
    if (/^(?:https?:|mailto:|#)/.test(rawTarget)) {
      continue
    }
    const target = rawTarget.split('#', 1)[0]
    const resolvedTarget = resolve(dirname(absolutePath), target)
    if (!existsSync(resolvedTarget)) {
      errors.push(`${relativePath}: broken local link ${rawTarget}`)
    }
  }
}

export function validateProjectDocs({
  root,
  commitExists = commit => defaultCommitExists(root, commit),
}) {
  const errors = []

  for (const relativePath of REQUIRED_FILES) {
    if (!existsSync(resolve(root, relativePath))) {
      errors.push(`Missing required file: ${relativePath}`)
    }
  }

  for (const relativePath of STATUS_FILES) {
    const absolutePath = resolve(root, relativePath)
    if (!existsSync(absolutePath)) {
      continue
    }
    const text = readFileSync(absolutePath, 'utf8')
    for (const metadata of REQUIRED_METADATA) {
      if (!text.includes(metadata)) {
        errors.push(`${relativePath}: missing metadata ${metadata}`)
      }
    }
    for (const heading of REQUIRED_HEADINGS) {
      if (!text.includes(heading)) {
        errors.push(`${relativePath}: missing heading ${heading}`)
      }
    }
    if (VAGUE_PATTERN.test(text)) {
      errors.push(`${relativePath}: contains vague placeholder`)
    }
    const commit = text.match(/^- 最后验证提交：`?(ROOT|[0-9a-f]{7,40})`?\s*$/m)?.[1]
    if (!commit) {
      errors.push(`${relativePath}: missing valid verification commit`)
    }
    else if (!commitExists(commit)) {
      errors.push(`${relativePath}: verification commit does not exist: ${commit}`)
    }
  }

  for (const relativePath of LINK_FILES) {
    validateLinks(root, relativePath, errors)
  }

  const indexPath = resolve(root, 'docs/README.md')
  if (existsSync(indexPath)) {
    const activeRows = readFileSync(indexPath, 'utf8')
      .split(/\r?\n/)
      .filter(line => /^\|.*\|\s*ACTIVE\s*\|/.test(line))
    for (const row of activeRows) {
      if (!row.includes('project/status/')) {
        errors.push('docs/README.md: ACTIVE index row lacks workstream status link')
      }
    }
  }

  return errors
}

const modulePath = fileURLToPath(import.meta.url)
if (process.argv[1] && resolve(process.argv[1]) === modulePath) {
  const errors = validateProjectDocs({ root: process.cwd() })
  if (errors.length > 0) {
    for (const error of errors) {
      console.error(`- ${error}`)
    }
    process.exitCode = 1
  }
  else {
    console.log('Project documentation verification passed')
  }
}
