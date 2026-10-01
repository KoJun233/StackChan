const assert = require('node:assert/strict')
const fs = require('node:fs')
const { chromium } = require('playwright')

const origin = 'http://host.docker.internal:8080'
const date = '2026-10-01T16:00:00Z'
const roleId = '11111111-1111-4111-8111-111111111111'
const evidence = { origin, browser: 'Chromium', viewports: [], blockedWrites: 0, pageErrors: 0 }
let stage = 'start'
let voiceMode = 'live'
let deviceId
let reminderTotal = 3

function event(source, name, elapsedMs, durationMs = null, diagnosticCode = null) {
  return { source, stage: name, elapsedMs, durationMs, diagnosticCode, failureCode: null, occurredAt: date }
}

const syntheticTurns = [
  {
    turnId: 'fixture-manual', status: 'COMPLETED', failureCode: null, startedAt: date, updatedAt: date,
    events: [event('DEVICE', 'SPEECH_CAPTURED', 1000), event('SERVER', 'ASR_COMPLETED', null, 550),
      event('SERVER', 'LLM_COMPLETED', null, 900), event('SERVER', 'TTS_COMPLETED', null, 300),
      event('DEVICE', 'PLAYBACK_STARTED', 3500), event('DEVICE', 'MANUAL_INPUT_READY', 5000)],
  },
  {
    turnId: 'fixture-wake', status: 'COMPLETED', failureCode: null, startedAt: date, updatedAt: date,
    events: [event('SERVER', 'ASR_COMPLETED', null), event('DEVICE', 'LISTENING_RESUMED', 5000)],
  },
  {
    turnId: 'fixture-local', status: 'CANCELLED', failureCode: null, startedAt: date, updatedAt: date,
    events: [event('SERVER', 'LLM_COMPLETED', null, null, 'deterministic_action')],
  },
  {
    turnId: 'fixture-failed', status: 'FAILED', failureCode: 'ASR_PROVIDER_FAILED', startedAt: date, updatedAt: date,
    events: [event('SERVER', 'ASR_COMPLETED', null, null, 'provider_request_rejected')],
  },
]

function reminder(id, source, status) {
  return {
    id, content: `合成验收-${source}-${status}`, deviceId, roleId, source, status,
    attemptCount: 0, failureCode: null, recurrenceInterval: 1, recurrenceType: 'NONE',
    scheduledAt: date, createdAt: date, updatedAt: date, zoneId: 'Asia/Shanghai',
    lastCompletedAt: null, lastOutcome: null, proactiveTopicKey: null, proactiveGenerationStatus: null,
    proactiveSourceName: null, proactiveSourceTitle: null, proactiveSourceUrl: null,
    proactiveSourcePublishedAt: null, proactiveSourceRetrievedAt: null,
  }
}

async function checkLayout(page, label, record) {
  await page.evaluate(() => document.fonts.ready)
  const dimensions = await page.evaluate(() => ({
    width: window.innerWidth, documentWidth: document.documentElement.scrollWidth,
    bodyWidth: document.body.scrollWidth,
  }))
  assert.ok(dimensions.documentWidth <= dimensions.width + 2, `${label}: document horizontal overflow`)
  assert.ok(dimensions.bodyWidth <= dimensions.width + 2, `${label}: body horizontal overflow`)
  record.layouts.push({ label, ...dimensions })
  await page.screenshot({ path: `/evidence/${record.name}-${label}.png`, fullPage: true, animations: 'disabled' })
}

async function checkPagination(page, record) {
  const metrics = await page.locator('[data-slot="pagination"]').evaluate((root) => {
    const bounds = root.getBoundingClientRect()
    return {
      width: bounds.width,
      controls: [...root.querySelectorAll('button, input, [role="combobox"]')].map((element) => {
        const rect = element.getBoundingClientRect()
        return { width: rect.width, left: rect.left - bounds.left, right: rect.right - bounds.left }
      }),
    }
  })
  assert.ok(metrics.controls.length >= 7, 'All pagination controls must remain available')
  for (const control of metrics.controls) {
    assert.ok(control.width >= 24, 'Pagination controls must not collapse')
    assert.ok(control.left >= -1 && control.right <= metrics.width + 1, 'Pagination control must not be clipped')
  }
  record.pagination = metrics
}

async function run() {
  const credentials = JSON.parse(fs.readFileSync(0, 'utf8'))
  const browser = await chromium.launch({ headless: true })
  evidence.browserVersion = browser.version()
  evidence.playwrightVersion = require('playwright/package.json').version
  try {
    for (const viewport of [{ name: 'desktop', width: 1440, height: 900 }, { name: 'mobile', width: 390, height: 844 }]) {
      const record = { name: viewport.name, width: viewport.width, height: viewport.height, layouts: [], checks: [] }
      evidence.viewports.push(record)
      const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height }, locale: 'zh-CN', timezoneId: 'Asia/Shanghai' })
      const page = await context.newPage()
      page.on('pageerror', () => { evidence.pageErrors += 1 })
      voiceMode = 'live'
      reminderTotal = 3
      await context.route('**/*', async (route) => {
        const request = route.request()
        const url = new URL(request.url())
        if (url.origin !== origin) return route.abort()
        if (!['GET', 'HEAD'].includes(request.method()) && !['/api/v1/auth/login', '/api/v1/auth/logout'].includes(url.pathname)) {
          evidence.blockedWrites += 1
          return route.abort()
        }
        if (request.method() !== 'GET') return route.continue()
        if (url.pathname === '/api/v1/devices') {
          const response = await route.fetch()
          assert.equal(response.status(), 200, 'Devices GET must be authenticated')
          const data = await response.json()
          assert.ok(data.devices.length > 0, 'At least one live device is needed')
          deviceId = data.devices[0].id
          const device = data.devices[0]
          record.deviceSafety = { firmwareVersion: device.firmwareVersion, safetyState: device.safetyState,
            motionState: device.body.motionState, calibrated: device.body.calibrated, failureCode: device.body.lastFailureCode }
          assert.equal(device.body.motionState, 'DISABLED', 'Read-only acceptance must leave device motion disabled')
          data.devices = data.devices.map((item, index) => ({ ...item, displayName: `验收设备${index + 1}` }))
          return route.fulfill({ response, json: data })
        }
        if (url.pathname === '/api/v1/roles') {
          return route.fulfill({ json: [{ id: roleId, name: '合成验收伙伴', archivedAt: null, defaultRole: true }] })
        }
        if (url.pathname.startsWith('/api/v1/memories/usage/')) return route.fulfill({ json: { memories: [] } })
        if (url.pathname.endsWith('/voice-turns') && voiceMode !== 'live') {
          if (voiceMode === 'error') return route.fulfill({ status: 503, json: { message: '合成诊断不可用' } })
          return route.fulfill({ json: { turns: voiceMode === 'empty' ? [] : syntheticTurns } })
        }
        if (url.pathname === '/api/v1/reminders') {
          let list = [reminder('fixture-pending', 'FOLLOW_UP', 'PENDING'), reminder('fixture-expired', 'FOLLOW_UP', 'EXPIRED'), reminder('fixture-user', 'USER', 'PENDING')]
          if (url.searchParams.get('status')) list = list.filter(item => item.status === url.searchParams.get('status'))
          return route.fulfill({ json: { list, total: url.searchParams.get('status') ? list.length : reminderTotal } })
        }
        return route.continue()
      })
      try {
        stage = `${viewport.name}-login`
        await page.goto(`${origin}/#/devices/overview`, { waitUntil: 'networkidle' })
        await page.locator('input[placeholder="用户名"]:visible').fill(credentials.username)
        await page.locator('input[placeholder="密码"]:visible').fill(credentials.password)
        await page.getByRole('button', { name: '登录', exact: true }).filter({ visible: true }).click()
        await page.getByRole('button', { name: '交互诊断', exact: true }).first().waitFor()
        const navigate = async (path) => {
          const hash = page.url().includes('#/')
          await page.goto(`${origin}${hash ? '/#' : ''}${path}`, { waitUntil: 'networkidle' })
        }

        stage = `${viewport.name}-live-diagnostics`
        await page.getByRole('button', { name: '交互诊断', exact: true }).first().click()
        await page.getByText('验收设备1 的最近语音回合', { exact: true }).first().waitFor()
        await page.waitForLoadState('networkidle')
        await checkLayout(page, 'live-diagnostics', record)
        record.checks.push('Live authenticated device/voice GET; device names redacted, memory references omitted')

        stage = `${viewport.name}-synthetic-diagnostics`
        voiceMode = 'fixture'
        await page.getByRole('button', { name: '交互诊断', exact: true }).first().click()
        const manual = page.locator('[data-turn-id="fixture-manual"]')
        await manual.waitFor()
        assert.ok((await manual.innerText()).includes('2.50 秒'), 'Same-device latency is displayed')
        for (const text of ['语音识别完成', '回答生成完成', '语音合成完成', '手动输入就绪', '0.55 秒', '0.90 秒', '0.30 秒']) {
          assert.ok((await manual.innerText()).includes(text), 'Independent stage timing or manual-ready is missing')
        }
        assert.ok((await page.locator('[data-turn-id="fixture-wake"]').innerText()).includes('未知'), 'Missing old timing is unknown')
        assert.ok((await page.locator('[data-turn-id="fixture-wake"]').innerText()).includes('恢复聆听'), 'Wake-ready is distinct')
        assert.ok((await page.locator('[data-turn-id="fixture-local"]').innerText()).includes('本地指令'), 'Local action is not model latency')
        assert.ok((await page.locator('[data-turn-id="fixture-failed"]').innerText()).includes('provider_request_rejected'), 'Safe error code is displayed')
        await checkLayout(page, 'fixture-diagnostics', record)
        record.checks.push('Synthetic stages, unknown legacy timing, local action, cancellation/failure, manual versus wake readiness')

        for (const mode of ['empty', 'error']) {
          stage = `${viewport.name}-diagnostics-${mode}`
          voiceMode = mode
          await page.getByRole('button', { name: '交互诊断', exact: true }).first().click()
          await page.getByText(mode === 'empty' ? '暂无语音回合诊断数据' : '诊断未获取', { exact: true }).first().waitFor()
          await checkLayout(page, `diagnostics-${mode}`, record)
        }
        await page.locator('[data-sonner-toast]').waitFor({ state: 'hidden', timeout: 15000 })
        stage = `${viewport.name}-speech`
        await navigate('/settings/speech')
        await page.getByRole('tab', { name: '唤醒与录音' }).click()
        await page.getByText('唤醒灵敏度', { exact: true }).waitFor()
        await page.locator('#speech-settings-form').getByRole('combobox').click()
        await page.getByRole('option', { name: '稳健（推荐）', exact: true }).waitFor()
        await page.getByRole('option', { name: '灵敏', exact: true }).waitFor()
        await page.keyboard.press('Escape')
        await checkLayout(page, 'speech', record)
        record.checks.push('Live speech settings loaded; audio tab only, no save/test/install operation')

        stage = `${viewport.name}-reminders`
        await navigate('/reminders')
        const followUp = page.getByRole('row').filter({ hasText: '合成验收-FOLLOW_UP-PENDING' })
        await followUp.waitFor()
        await checkPagination(page, record)
        await checkLayout(page, 'reminders-initial', record)
        assert.equal(await followUp.getByRole('button').count(), 1, 'Follow-up must not expose ordinary edit')
        await followUp.getByRole('button').click()
        await page.getByText('跳过提醒', { exact: true }).waitFor()
        assert.equal(await page.getByText('10 分钟后提醒', { exact: true }).count(), 0, 'Follow-up must not expose snooze')
        await page.keyboard.press('Escape')
        assert.ok((await page.getByRole('row').filter({ hasText: '合成验收-FOLLOW_UP-EXPIRED' }).innerText()).includes('已过期'), 'Expired follow-up must be distinct')
        assert.equal(await page.getByRole('row').filter({ hasText: '合成验收-USER-PENDING' }).getByRole('button').count(), 2, 'Ordinary reminder retains edit')
        await checkLayout(page, 'reminders', record)
        record.checks.push('Synthetic confirmed follow-up/expired/ordinary reminder; menus inspected without selecting commands')
        reminderTotal = 2500
        await page.getByRole('button', { name: '筛选', exact: true }).click()
        await page.getByText('共 2500 条', { exact: true }).waitFor()
        const pager = page.locator('[data-slot="pagination-content"]')
        const controls = pager.getByRole('button')
        for (const control of [controls.first(), controls.last()]) {
          await control.scrollIntoViewIfNeeded()
          const bounds = await pager.boundingBox()
          const rect = await control.boundingBox()
          assert.ok(rect.x >= bounds.x - 1 && rect.x + rect.width <= bounds.x + bounds.width + 1,
            'Large-page first/last navigation must be reachable')
        }
        await checkLayout(page, 'large-pagination', record)
        record.checks.push('Synthetic 2500-row pagination; first/last navigation reachable without changing any business state')
      }
      catch (error) {
        evidence.failedStage = stage
        evidence.failureKind = error.name
        evidence.failureReason = String(error.message).split('\n')[0]
          .split(credentials.username).join('[redacted]').split(credentials.password).join('[redacted]')
        if (error.code === 'ERR_ASSERTION') evidence.assertion = error.message
        throw error
      }
      finally {
        const csrfResponse = await context.request.get(`${origin}/api/v1/auth/csrf`)
        const csrf = await csrfResponse.json()
        const logout = await context.request.post(`${origin}/api/v1/auth/logout`, { headers: { [csrf.headerName]: csrf.token } })
        assert.ok(logout.ok(), 'Temporary browser session logout failed')
        const protectedResponse = await context.request.get(`${origin}/api/v1/devices`)
        assert.equal(protectedResponse.status(), 401, 'Logged-out test session must lose API access')
        record.sessionInvalidated = true
        await context.close()
      }
    }
    assert.equal(evidence.blockedWrites, 0, 'Unexpected business write attempted')
    assert.equal(evidence.pageErrors, 0, 'Unexpected browser runtime error')
    evidence.result = 'PASS'
  }
  finally {
    await browser.close()
  }
}

run().catch(() => {
  evidence.result = 'FAIL'
  evidence.failedStage ??= stage
  process.exitCode = 1
}).finally(() => {
  fs.writeFileSync('/evidence/result.json', JSON.stringify(evidence, null, 2))
  console.log(JSON.stringify(evidence))
})
