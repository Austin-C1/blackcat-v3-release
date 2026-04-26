import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

function readSource(relativePath: string): string {
  return fs.readFileSync(fileURLToPath(new URL(relativePath, import.meta.url)), 'utf8')
}

describe('frontend source rules', () => {
  it('api service should not use ApiResponse<any>', () => {
    const source = readSource('../src/services/api.ts')
    expect(source).not.toContain('ApiResponse<any>')
  })

  it('position list should avoid duplicate sell form state and O(n*m) removals', () => {
    const source = readSource('../src/pages/PositionList.tsx')
    expect(source).not.toContain('const [sellQuantity')
    expect(source).not.toContain('const [limitPrice')
    expect(source).not.toContain('removedKeys.includes(')
  })

  it('tables should not build row keys from array indexes', () => {
    const positionList = readSource('../src/pages/PositionList.tsx')
    const leaderList = readSource('../src/pages/LeaderList.tsx')

    expect(positionList).not.toContain('rowKey={(record, index)')
    expect(leaderList).not.toContain('rowKey={(record, index)')
  })

  it('copy trading list should memoize account-first grouping', () => {
    const source = readSource('../src/pages/CopyTradingList.tsx')
    expect(source).toContain('useMemo')
    expect(source).toContain('const visibleCopyTradings = useMemo(() => {')
    expect(source).toMatch(/const accountGroups = useMemo(?:<[^>]+>)?\(\(\) => \{/)
  })

  it('account balance response should reuse wallet balance shape instead of duplicating it', () => {
    const source = readSource('../src/types/index.ts')
    expect(source).toContain('export interface AccountBalanceResponse extends WalletBalanceResponse')
  })

  it('temporary console logging should be removed from checked frontend files', () => {
    const files = [
      '../src/pages/PositionList.tsx',
      '../src/pages/BacktestDetail.tsx',
      '../src/pages/BacktestList.tsx',
      '../src/services/websocket.ts'
    ]

    for (const file of files) {
      const source = readSource(file)
      expect(source).not.toMatch(/console\.(log|warn|error)/)
    }
  })

  it('notification template sources should not keep the removed crypto tail template', () => {
    const zhCn = readSource('../src/locales/zh-CN/common.json')
    const zhTw = readSource('../src/locales/zh-TW/common.json')
    const notificationPage = readSource('../src/pages/NotificationSettingsPage.tsx')

    expect(zhCn).not.toContain('"CRYPTO_TAIL_SUCCESS"')
    expect(zhCn).toContain('"strategy_name": "策略名称"')
    expect(zhCn).not.toContain('"strategy_name": "加密价差策略名称"')

    expect(zhTw).not.toContain('"CRYPTO_TAIL_SUCCESS"')
    expect(zhTw).toContain('"strategy_name": "策略名稱"')
    expect(zhTw).not.toContain('"strategy_name": "加密價差策略名稱"')
    expect(notificationPage).not.toContain('CRYPTO_TAIL_SUCCESS')
  })

  it('frontend branding should not default back to PolyHermes links or package names', () => {
    const packageJson = readSource('../package.json')
    const viteConfig = readSource('../vite.config.ts')
    const cryptoTailPage = readSource('../src/pages/CryptoTailStrategyList.tsx')

    expect(packageJson).toContain('"name": "blackcat-v1-frontend"')
    expect(packageJson).not.toContain('"name": "polyhermes-frontend"')

    expect(viteConfig).not.toContain('https://github.com/WrBug/PolyHermes')
    expect(viteConfig).toContain("const GITHUB_REPO_URL = env.GITHUB_REPO_URL || ''")

    expect(cryptoTailPage).not.toContain('getVersionInfo')
    expect(cryptoTailPage).not.toContain('configGuide')
  })
})
