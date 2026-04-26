/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AddModal from './AddModal'

const fetchAccountsMock = vi.fn()
const leadersListMock = vi.fn()
const copyTradingListMock = vi.fn()
const copyTradingCreateMock = vi.fn()

const accountStoreState = {
  accounts: [] as Array<{
    id: number
    accountName?: string
    walletAddress: string
  }>,
}

vi.mock('react-responsive', () => ({
  useMediaQuery: () => false,
}))

vi.mock('../../store/accountStore', () => ({
  useAccountStore: () => ({
    accounts: accountStoreState.accounts,
    fetchAccounts: fetchAccountsMock,
  }),
}))

vi.mock('../../services/api', () => ({
  apiService: {
    leaders: {
      list: (...args: unknown[]) => leadersListMock(...args),
    },
    copyTrading: {
      list: (...args: unknown[]) => copyTradingListMock(...args),
      create: (...args: unknown[]) => copyTradingCreateMock(...args),
    },
  },
}))

describe('AddModal', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    })

    Object.defineProperty(window, 'getComputedStyle', {
      writable: true,
      value: vi.fn().mockImplementation(() => ({
        getPropertyValue: () => '',
        overflow: 'visible',
        overflowX: 'visible',
        overflowY: 'visible',
      })),
    })

    accountStoreState.accounts = [
      {
        id: 1,
        accountName: 'Smart001-3459',
        walletAddress: '0x1111111111111111111111111111111111111111',
      },
      {
        id: 2,
        accountName: 'Smart002-8888',
        walletAddress: '0x2222222222222222222222222222222222222222',
      },
    ]

    fetchAccountsMock.mockReset()
    leadersListMock.mockReset()
    copyTradingListMock.mockReset()
    copyTradingCreateMock.mockReset()

    leadersListMock.mockResolvedValue({
      data: {
        code: 0,
        data: {
          list: [
            {
              id: 101,
              leaderAddress: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
              leaderName: 'debased',
              copyTradingCount: 1,
              monitoringEnabled: true,
              backtestCount: 0,
              createdAt: 0,
              updatedAt: 0,
            },
            {
              id: 102,
              leaderAddress: '0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
              leaderName: 'DrPufferfish',
              copyTradingCount: 1,
              monitoringEnabled: true,
              backtestCount: 0,
              createdAt: 0,
              updatedAt: 0,
            },
            {
              id: 103,
              leaderAddress: '0xcccccccccccccccccccccccccccccccccccccccc',
              leaderName: 'Ken',
              copyTradingCount: 0,
              monitoringEnabled: true,
              backtestCount: 0,
              createdAt: 0,
              updatedAt: 0,
            },
          ],
        },
      },
    })

    copyTradingListMock.mockResolvedValue({
      data: {
        code: 0,
        data: {
          list: [
            {
              id: 1,
              accountId: 1,
              walletAddress: '0x1111111111111111111111111111111111111111',
              leaderId: 101,
              leaderAddress: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
              enabled: true,
              followSettingsEnabled: true,
              maxOrderSize: '100',
              minOrderSize: '1',
              maxDailyLoss: '0',
              maxDailyOrders: 20,
              priceTolerance: '0.02',
              delaySeconds: 0,
              pollIntervalSeconds: 15,
              useWebSocket: true,
              websocketReconnectInterval: 5,
              websocketMaxRetries: 10,
              supportSell: true,
              pushFailedOrders: false,
              pushFilteredOrders: false,
              createdAt: 0,
              updatedAt: 0,
            },
            {
              id: 2,
              accountId: 2,
              walletAddress: '0x2222222222222222222222222222222222222222',
              leaderId: 102,
              leaderAddress: '0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
              enabled: true,
              followSettingsEnabled: true,
              maxOrderSize: '100',
              minOrderSize: '1',
              maxDailyLoss: '0',
              maxDailyOrders: 20,
              priceTolerance: '0.02',
              delaySeconds: 0,
              pollIntervalSeconds: 15,
              useWebSocket: true,
              websocketReconnectInterval: 5,
              websocketMaxRetries: 10,
              supportSell: true,
              pushFailedOrders: false,
              pushFilteredOrders: false,
              createdAt: 0,
              updatedAt: 0,
            },
          ],
        },
      },
    })
  })

  afterEach(() => {
    cleanup()
  })

  it('does not reinitialize when the prefilled leader id stays the same', async () => {
    const { rerender } = render(
      <AddModal
        open
        onClose={() => {}}
        preFilledConfig={{ leaderId: 5 }}
      />,
    )

    await waitFor(() => {
      expect(fetchAccountsMock).toHaveBeenCalledTimes(1)
      expect(leadersListMock).toHaveBeenCalledTimes(1)
      expect(copyTradingListMock).toHaveBeenCalledTimes(1)
    })

    rerender(
      <AddModal
        open
        onClose={() => {}}
        preFilledConfig={{ leaderId: 5 }}
      />,
    )

    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(fetchAccountsMock).toHaveBeenCalledTimes(1)
    expect(leadersListMock).toHaveBeenCalledTimes(1)
    expect(copyTradingListMock).toHaveBeenCalledTimes(1)
  })

  it('hides leaders that are already linked to the selected account', async () => {
    render(
      <AddModal
        open
        onClose={() => {}}
        preFilledConfig={{ accountId: 1 }}
      />,
    )

    await waitFor(() => {
      expect(copyTradingListMock).toHaveBeenCalledWith({})
      expect(leadersListMock).toHaveBeenCalled()
    })

    const comboboxes = screen.getAllByRole('combobox')
    fireEvent.mouseDown(comboboxes[1])

    await waitFor(() => {
      expect(screen.getByText('DrPufferfish (0xbbbb...bbbb)')).toBeTruthy()
      expect(screen.getByText('Ken (0xcccc...cccc)')).toBeTruthy()
    })

    expect(screen.queryByText('debased (0xaaaa...aaaa)')).toBeNull()
  })

  it('creates a binding-only config with follow settings disabled', async () => {
    copyTradingCreateMock.mockResolvedValue({
      data: {
        code: 0,
      },
    })

    render(
      <AddModal
        open
        onClose={() => {}}
        preFilledConfig={{ accountId: 1, leaderId: 103 }}
      />,
    )

    await waitFor(() => {
      expect(copyTradingListMock).toHaveBeenCalled()
      expect(leadersListMock).toHaveBeenCalled()
    })

    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }))

    await waitFor(() => {
      expect(copyTradingCreateMock).toHaveBeenCalledWith(
        expect.objectContaining({
          accountId: 1,
          leaderId: 103,
          followSettingsEnabled: false,
          enabled: true,
        }),
      )
    })
  })
})
