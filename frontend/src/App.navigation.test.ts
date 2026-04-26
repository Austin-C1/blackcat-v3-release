import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

describe('app navigation', () => {
  it('does not expose the removed statistics page', () => {
    const appSource = readFileSync(join(process.cwd(), 'src', 'App.tsx'), 'utf8')
    const layoutSource = readFileSync(join(process.cwd(), 'src', 'components', 'Layout.tsx'), 'utf8')

    expect(appSource).not.toContain("import('./pages/Statistics')")
    expect(appSource).not.toContain('path="/statistics"')
    expect(layoutSource).not.toContain("key: '/statistics'")
    expect(layoutSource).not.toContain("t('menu.statistics')")
  })

  it('exposes market betting query page and Blackcat V3 brand', () => {
    const appSource = readFileSync(join(process.cwd(), 'src', 'App.tsx'), 'utf8')
    const layoutSource = readFileSync(join(process.cwd(), 'src', 'components', 'Layout.tsx'), 'utf8')

    expect(appSource).toContain("import('./pages/MarketBettingQuery')")
    expect(appSource).toContain('path="/market-betting-query"')
    expect(layoutSource).toContain("key: '/market-betting-query'")
    expect(layoutSource).toContain('盘口投注额查询')
    expect(layoutSource).toContain('黑猫V3')
    expect(layoutSource).not.toContain('黑猫V2')
  })

  it('exposes system update page under system settings', () => {
    const appSource = readFileSync(join(process.cwd(), 'src', 'App.tsx'), 'utf8')
    const layoutSource = readFileSync(join(process.cwd(), 'src', 'components', 'Layout.tsx'), 'utf8')
    const announcementsSource = readFileSync(join(process.cwd(), 'src', 'pages', 'Announcements.tsx'), 'utf8')

    expect(appSource).toContain("import('./pages/SystemUpdate')")
    expect(appSource).toContain('path="/system-settings/update"')
    expect(layoutSource).not.toContain("key: '/system-settings/update'")
    expect(announcementsSource).toContain("navigate('/system-settings/update')")
    expect(announcementsSource).toContain('更新')
  })
})
