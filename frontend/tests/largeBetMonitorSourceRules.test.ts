import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

function readSource(relativePath: string): string {
  return fs.readFileSync(fileURLToPath(new URL(relativePath, import.meta.url)), 'utf8')
}

describe('large bet monitor source rules', () => {
  it('adds an independent route and menu entry', () => {
    const appSource = readSource('../src/App.tsx')
    const layoutSource = readSource('../src/components/Layout.tsx')

    expect(appSource).toContain("const LargeBetMonitor = lazy(() => import('./pages/LargeBetMonitor'))")
    expect(appSource).toContain('path="/large-bet-monitor"')
    expect(layoutSource).toContain("key: '/large-bet-monitor'")
    expect(layoutSource).toContain("t('menu.largeBetMonitor')")
  })

  it('uses a separate API namespace from existing notification settings', () => {
    const apiSource = readSource('../src/services/api.ts')

    expect(apiSource).toContain('largeBetMonitor: {')
    expect(apiSource).toContain("'/system/large-bet-monitor/config'")
    expect(apiSource).toContain("'/system/large-bet-monitor/records/list'")
    expect(apiSource).toContain("'/system/large-bet-monitor/test'")
  })

  it('renders configurable thresholds sports and records table', () => {
    const pageSource = readSource('../src/pages/LargeBetMonitor.tsx')

    expect(pageSource).toContain('singleTradeThreshold')
    expect(pageSource).toContain('cumulativeTradeThreshold')
    expect(pageSource).toContain('rollingWindowMinutes')
    expect(pageSource).toContain('footballEnabled')
    expect(pageSource).toContain('basketballEnabled')
    expect(pageSource).toContain('profileUrl')
  })

  it('keeps market query under system notification robot settings', () => {
    const layoutSource = readSource('../src/components/Layout.tsx')
    const pageSource = readSource('../src/pages/MarketBettingQuery.tsx')

    expect(layoutSource).not.toContain("key: '/market-betting-query',\n      icon: <FundOutlined />")
    expect(layoutSource).toContain("key: '/market-betting-query'")
    expect(pageSource).toContain('marketBettingQueryEnabled')
    expect(pageSource).toContain('查询机器人')
  })

  it('moves copy trading notification filters to robot settings', () => {
    const addSource = readSource('../src/pages/CopyTradingOrders/AddModal.tsx')
    const editSource = readSource('../src/pages/CopyTradingOrders/EditModal.tsx')
    const notificationSettingsSource = readSource('../src/pages/NotificationSettingsPage.tsx')
    const telegramFormSource = readSource('../src/components/notifications/TelegramConfigForm.tsx')

    expect(addSource).not.toContain('消息筛选')
    expect(editSource).not.toContain('消息筛选')
    expect(notificationSettingsSource).toContain('copyTradingCategories')
    expect(notificationSettingsSource).toContain('copyTradingNotificationTypes')
    expect(notificationSettingsSource).toContain('handleUpdateRobotFilters')
    expect(notificationSettingsSource).toContain('renderFilterSelect')
    expect(telegramFormSource).toContain('监控分类')
    expect(telegramFormSource).toContain('消息类型')
  })
})
