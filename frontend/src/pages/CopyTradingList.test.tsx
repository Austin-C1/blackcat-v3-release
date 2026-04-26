/* @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CopyTradingList from './CopyTradingList'

const navigateMock = vi.fn()
const fetchAccountsMock = vi.fn()
const leadersListMock = vi.fn()
const copyTradingListMock = vi.fn()
const leaderGroupControlsMock = vi.fn()
const loadCopyTradingStatisticsMapMock = vi.fn()
const addModalPropsSpy = vi.fn()

let searchParamsState = new URLSearchParams()

const accountStoreState = {
  accounts: [] as Array<{
    id: number
    accountName?: string
    remark?: string
    walletAddress: string
    proxyAddress: string
    apiKeyConfigured: boolean
    apiSecretConfigured: boolean
    apiPassphraseConfigured: boolean
    totalPnl?: string
  }>,
}

vi.mock('react-responsive', () => ({
  useMediaQuery: () => false,
}))

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigateMock,
  useSearchParams: () => [searchParamsState],
}))

vi.mock('../store/accountStore', () => ({
  useAccountStore: () => ({
    accounts: accountStoreState.accounts,
    fetchAccounts: fetchAccountsMock,
  }),
}))

vi.mock('../services/api', () => ({
  apiService: {
    leaders: {
      list: (...args: unknown[]) => leadersListMock(...args),
    },
    copyTrading: {
      list: (...args: unknown[]) => copyTradingListMock(...args),
      leaderGroupControls: (...args: unknown[]) => leaderGroupControlsMock(...args),
      updateStatus: vi.fn(),
      delete: vi.fn(),
      updateLeaderGroupControl: vi.fn(),
      restartLeaderGroup: vi.fn(),
      closeLeaderGroup: vi.fn(),
    },
  },
}))

vi.mock('../utils/copyTradingStatistics', () => ({
  loadCopyTradingStatisticsMap: (...args: unknown[]) => loadCopyTradingStatisticsMapMock(...args),
}))

vi.mock('./CopyTradingOrders/index', () => ({
  default: () => null,
}))

vi.mock('./CopyTradingOrders/StatisticsModal', () => ({
  default: () => null,
}))

vi.mock('./CopyTradingOrders/EditModal', () => ({
  default: () => null,
}))

vi.mock('./CopyTradingOrders/AddModal', () => ({
  default: (props: any) => {
    addModalPropsSpy(props)
    return props.open ? (
      <div data-testid="add-modal-props">{JSON.stringify(props.preFilledConfig ?? null)}</div>
    ) : null
  },
}))

function createCopyTrading(overrides: Partial<any>) {
  return {
    id: 1,
    accountId: 1,
    accountName: 'Smart001-3459',
    walletAddress: '0x1111111111111111111111111111111111111111',
    leaderId: 101,
    leaderName: 'debased',
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
    configName: '配置-1',
    pushFailedOrders: false,
    pushFilteredOrders: false,
    followRules: [],
    createdAt: 0,
    updatedAt: 0,
    ...overrides,
  }
}

function createLeader(overrides: Partial<any>) {
  return {
    id: 101,
    leaderAddress: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    leaderName: 'debased',
    category: 'sports',
    customGroup: 'Desk A',
    copyTradingCount: 1,
    monitoringEnabled: true,
    backtestCount: 0,
    totalPnl: '1230',
    createdAt: 0,
    updatedAt: 0,
    ...overrides,
  }
}

function createLeaderControl(overrides: Partial<any>) {
  return {
    leaderId: 101,
    leaderName: 'debased',
    leaderAddress: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    autoPauseEnabled: true,
    profitTakeEnabled: false,
    profitTakePrice: '0.95',
    status: 'ACTIVE',
    pausedReason: null,
    lastPeakPnl: '310',
    currentPnl: '220',
    currentDrawdownPercent: '0',
    autoPausedAt: null,
    lastEvaluatedAt: null,
    trackedWindowDays: 7,
    drawdownThresholdPercent: '25',
    ...overrides,
  }
}

describe('CopyTradingList', () => {
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

    searchParamsState = new URLSearchParams()
    navigateMock.mockReset()
    fetchAccountsMock.mockReset()
    leadersListMock.mockReset()
    copyTradingListMock.mockReset()
    leaderGroupControlsMock.mockReset()
    loadCopyTradingStatisticsMapMock.mockReset()
    addModalPropsSpy.mockReset()

    accountStoreState.accounts = [
      {
        id: 1,
        accountName: 'Smart001-3459',
        remark: '长期观察账号',
        walletAddress: '0x1111111111111111111111111111111111111111',
        proxyAddress: '0x9999999999999999999999999999999999999999',
        apiKeyConfigured: true,
        apiSecretConfigured: true,
        apiPassphraseConfigured: true,
        totalPnl: '1860',
      },
      {
        id: 2,
        accountName: 'Smart002-8888',
        remark: '备用账号',
        walletAddress: '0x2222222222222222222222222222222222222222',
        proxyAddress: '0x8888888888888888888888888888888888888888',
        apiKeyConfigured: true,
        apiSecretConfigured: true,
        apiPassphraseConfigured: true,
        totalPnl: '920',
      },
    ]

    leadersListMock.mockResolvedValue({
      data: {
        code: 0,
        data: {
          list: [
            createLeader({
              id: 101,
              leaderName: 'debased',
              category: 'sports',
              customGroup: 'Desk A',
            }),
            createLeader({
              id: 102,
              leaderAddress: '0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
              leaderName: 'DrPufferfish',
              category: 'Desk A',
              customGroup: 'Desk B',
              totalPnl: '860',
            }),
          ],
        },
      },
    })

    copyTradingListMock.mockResolvedValue({
      data: {
        code: 0,
        data: {
          list: [
            createCopyTrading({
              id: 1,
              accountId: 1,
              accountName: 'Smart001-3459',
              leaderId: 101,
              leaderName: 'debased',
              configName: '配置-A1',
            }),
            createCopyTrading({
              id: 2,
              accountId: 1,
              accountName: 'Smart001-3459',
              leaderId: 102,
              leaderName: 'DrPufferfish',
              leaderAddress: '0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
              configName: '配置-A2',
            }),
            createCopyTrading({
              id: 3,
              accountId: 2,
              accountName: 'Smart002-8888',
              walletAddress: '0x2222222222222222222222222222222222222222',
              leaderId: 101,
              leaderName: 'debased',
              configName: '配置-B1',
            }),
          ],
        },
      },
    })

    leaderGroupControlsMock.mockResolvedValue({
      data: {
        code: 0,
        data: {
          list: [
            createLeaderControl({
              leaderId: 101,
              leaderName: 'debased',
            }),
            createLeaderControl({
              leaderId: 102,
              leaderName: 'DrPufferfish',
              leaderAddress: '0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
              currentPnl: '90',
              lastPeakPnl: '140',
            }),
          ],
        },
      },
    })

    loadCopyTradingStatisticsMapMock.mockResolvedValue({
      1: { totalPnl: '40' },
      2: { totalPnl: '60' },
      3: { totalPnl: '80' },
    })
  })

  afterEach(() => {
    cleanup()
  })

  it('renders copy tradings with account-first grouping', async () => {
    render(<CopyTradingList />)

    await waitFor(() => {
      expect(copyTradingListMock).toHaveBeenCalled()
    })

    expect(screen.getByText('Smart001-3459')).toBeTruthy()
    expect(screen.getByText('Smart002-8888')).toBeTruthy()
    expect(screen.getByText('已绑定 2 个 Leader')).toBeTruthy()
    expect(screen.getByText('已绑定 1 个 Leader')).toBeTruthy()
  })

  it('filters the page by custom group from search params', async () => {
    searchParamsState = new URLSearchParams('group=Desk%20A')

    render(<CopyTradingList />)

    await waitFor(() => {
      expect(copyTradingListMock).toHaveBeenCalled()
    })

    expect(screen.getAllByText('debased').length).toBeGreaterThan(0)
    expect(screen.queryByText('DrPufferfish')).toBeNull()
  })

  it('opens add modal with the clicked account prefilled', async () => {
    render(<CopyTradingList />)

    await waitFor(() => {
      expect(copyTradingListMock).toHaveBeenCalled()
    })

    fireEvent.click(screen.getAllByRole('button', { name: /给此账号新增/ })[0])

    const latestOpenCall = [...addModalPropsSpy.mock.calls]
      .map(([props]) => props)
      .reverse()
      .find((props) => props.open)

    expect(latestOpenCall?.preFilledConfig?.accountId).toBe(1)
  })

  it('prefers grouped copy trading pnl over stale account totals in the account summary', async () => {
    render(<CopyTradingList />)

    await waitFor(() => {
      expect(copyTradingListMock).toHaveBeenCalled()
    })

    expect(screen.getAllByText(/\+100 USDC/).length).toBeGreaterThan(0)
    expect(screen.queryByText(/\+1,860 USDC/)).toBeNull()
  })

  it('shows account remarks and separates lifetime pnl from seven-day pnl', async () => {
    render(<CopyTradingList />)

    await waitFor(() => {
      expect(copyTradingListMock).toHaveBeenCalled()
    })

    expect(screen.getByText('长期观察账号')).toBeTruthy()
    expect(screen.getAllByText('七日收益').length).toBeGreaterThan(0)
    expect(screen.getAllByText('7天最高点').length).toBeGreaterThan(0)
    expect(screen.getAllByText('七日收益 / 回撤').length).toBeGreaterThan(0)
    expect(screen.getAllByText(/总收益 \+40 USDC/).length).toBeGreaterThan(0)
    expect(screen.queryByText('当前收益 / 回撤')).toBeNull()
    expect(screen.queryByText(/当前收益 \+/)).toBeNull()
  })
})
