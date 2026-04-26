import { describe, expect, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(__dirname, '..')

describe('NotificationSettingsPage order filter settings', () => {
  it('exposes the order notification minimum amount setting on the notification page', () => {
    const source = fs.readFileSync(path.join(root, 'pages', 'NotificationSettingsPage.tsx'), 'utf8')

    expect(source).toContain('orderNotificationMinAmountUsdc')
    expect(source).toContain('updateOrderNotificationMinAmount')
    expect(source).toContain('InputNumber')
  })

  it('defines an API endpoint to save the order notification minimum amount', () => {
    const source = fs.readFileSync(path.join(root, 'services', 'api.ts'), 'utf8')

    expect(source).toContain('updateOrderNotificationMinAmount')
    expect(source).toContain('/system/config/order-notification-min-amount/update')
  })
})
