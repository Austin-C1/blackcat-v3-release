import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

describe('market betting query page', () => {
  const source = () => readFileSync(join(process.cwd(), 'src', 'pages', 'MarketBettingQuery.tsx'), 'utf8')

  it('preserves existing telegram routing fields when saving query bots', () => {
    const pageSource = source()

    expect(pageSource).toContain('copyTradingLeaderGroups: telegram.copyTradingLeaderGroups || []')
    expect(pageSource).toContain('copyTradingCategories: telegram.copyTradingCategories || []')
    expect(pageSource).toContain('copyTradingNotificationTypes: telegram.copyTradingNotificationTypes || []')
  })

  it('shows traded shares and traded amount without top holder links in market details', () => {
    const pageSource = source()

    expect(pageSource).toContain("title: '已成交 shares'")
    expect(pageSource).toContain("dataIndex: 'tradedAmount'")
    expect(pageSource).not.toContain('holder.profileUrl')
    expect(pageSource).not.toContain("title: 'Top 5 shares 持仓'")
  })

  it('sends selected event date when searching markets', () => {
    const pageSource = source()

    expect(pageSource).toContain('DatePicker')
    expect(pageSource).toContain("values.date?.format('YYYY-MM-DD')")
    expect(pageSource).toContain('date })')
    expect(pageSource).toContain('marketLimit: 100, date')
  })
})
