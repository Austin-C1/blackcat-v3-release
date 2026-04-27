import { lazy, Suspense, useEffect, useCallback, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { ConfigProvider, notification, Spin } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import zhTW from 'antd/locale/zh_TW'
import enUS from 'antd/locale/en_US'
import { useTranslation } from 'react-i18next'
import Layout from './components/Layout'
import { wsManager } from './services/websocket'
import type { OrderPushMessage } from './types'
import { apiService } from './services/api'
import { hasToken } from './utils'

const Login = lazy(() => import('./pages/Login'))
const ResetPassword = lazy(() => import('./pages/ResetPassword'))
const AccountList = lazy(() => import('./pages/AccountList'))
const UserList = lazy(() => import('./pages/UserList'))
const AccountImport = lazy(() => import('./pages/AccountImport'))
const AccountDetail = lazy(() => import('./pages/AccountDetail'))
const AccountEdit = lazy(() => import('./pages/AccountEdit'))
const LeaderList = lazy(() => import('./pages/LeaderList'))
const LeaderAdd = lazy(() => import('./pages/LeaderAdd'))
const LeaderEdit = lazy(() => import('./pages/LeaderEdit'))
const ConfigPage = lazy(() => import('./pages/ConfigPage'))
const PositionList = lazy(() => import('./pages/PositionList'))
const CopyTradingList = lazy(() => import('./pages/CopyTradingList'))
const CopyTradingStatistics = lazy(() => import('./pages/CopyTradingStatistics'))
const CopyTradingBuyOrders = lazy(() => import('./pages/CopyTradingBuyOrders'))
const CopyTradingSellOrders = lazy(() => import('./pages/CopyTradingSellOrders'))
const CopyTradingMatchedOrders = lazy(() => import('./pages/CopyTradingMatchedOrders'))
const FilteredOrdersList = lazy(() => import('./pages/FilteredOrdersList'))
const SystemSettings = lazy(() => import('./pages/SystemSettings'))
const NotificationSettingsPage = lazy(() => import('./pages/NotificationSettingsPage'))
const ApiHealthStatus = lazy(() => import('./pages/ApiHealthStatus'))
const RpcNodeSettings = lazy(() => import('./pages/RpcNodeSettings'))
const SystemUpdate = lazy(() => import('./pages/SystemUpdate'))
const Announcements = lazy(() => import('./pages/Announcements'))
const LargeBetMonitor = lazy(() => import('./pages/LargeBetMonitor'))
const MarketBettingQuery = lazy(() => import('./pages/MarketBettingQuery'))

// Keep auth pages outside the main app layout.
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const location = useLocation()
  const isAuthPage = location.pathname === '/login' || location.pathname === '/reset-password'

  if (isAuthPage) {
    return <>{children}</>
  }

  if (!hasToken()) {
    return <Navigate to="/login" replace />
  }

  return <Layout>{children}</Layout>
}

const RouteFallback: React.FC = () => (
  <div
    style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '50vh'
    }}
  >
    <Spin size="large" />
  </div>
)

const LazyRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <Suspense fallback={<RouteFallback />}>
    {children}
  </Suspense>
)

function App() {
  const { t, i18n } = useTranslation()
  const [isFirstUse, setIsFirstUse] = useState<boolean | null>(null)
  const [checking, setChecking] = useState(true)

  // Keep Ant Design locale aligned with the current i18n language.
  const getAntdLocale = () => {
    const lang = i18n.language || 'en'
    if (lang.startsWith('zh-CN')) return zhCN
    if (lang.startsWith('zh-TW') || lang.startsWith('zh-HK')) return zhTW
    return enUS
  }

  // Map order event types to display labels.
  const getOrderTypeText = useCallback((type: string): string => {
    switch (type) {
      case 'PLACEMENT':
        return t('order.create')
      case 'UPDATE':
        return t('order.update')
      case 'CANCELLATION':
        return t('order.cancel')
      default:
        return t('order.event')
    }
  }, [t])

  // Show order push notifications with readable summary text.
  const handleOrderPush = useCallback((message: OrderPushMessage) => {
    const { accountName, order, orderDetail, leaderName, configName } = message

    const orderTypeText = getOrderTypeText(order.type)
    const sideText = order.side === 'BUY' ? t('order.buy') : t('order.sell')
    const marketName = orderDetail?.marketName || order.market.substring(0, 8) + '...'

    let title = `${accountName} - ${orderTypeText}`
    if (leaderName || configName) {
      const parts: string[] = []
      if (configName) {
        parts.push(configName)
      }
      if (leaderName) {
        parts.push(`Leader: ${leaderName}`)
      }
      if (parts.length > 0) {
        title = `${accountName} (${parts.join(', ')}) - ${orderTypeText}`
      }
    }

    // Prefer filled size when the exchange already returned an average fill price.
    const size = orderDetail ? orderDetail.size : order.original_size
    const filled = orderDetail ? orderDetail.filled : order.size_matched
    const sizeNum = parseFloat(size).toFixed(2)
    const filledNum = parseFloat(filled).toFixed(2)
    const hasFilled = parseFloat(filled) > 0
    const price = orderDetail
      ? (orderDetail.avgFilledPrice ?? orderDetail.price)
      : (hasFilled
          ? (parseFloat(order.original_size) * parseFloat(order.price) / parseFloat(order.size_matched)).toString()
          : order.price)
    const priceStr = parseFloat(price).toFixed(4)
    const status = orderDetail?.status || 'UNKNOWN'
    const displaySize = (orderDetail?.avgFilledPrice || (orderDetail == null && hasFilled)) ? filledNum : sizeNum

    let description = `${t('order.market')}: ${marketName}\n${sideText} ${displaySize} @ ${priceStr}`

    if (orderDetail) {
      description += `\n${t('order.status')}: ${status}`
      if (parseFloat(filledNum) > 0) {
        description += ` | ${t('order.filled')}: ${filledNum}`
      }
      const remaining = (parseFloat(sizeNum) - parseFloat(filledNum)).toFixed(2)
      if (parseFloat(remaining) > 0) {
        description += ` | ${t('order.remaining')}: ${remaining}`
      }
    } else if (order.type === 'UPDATE' && parseFloat(order.size_matched) > 0) {
      description += `\n${t('order.filled')}: ${filledNum}`
    }

    let notificationType: 'info' | 'success' | 'warning' | 'error' = 'info'
    if (order.type === 'PLACEMENT') {
      notificationType = 'info'
    } else if (order.type === 'UPDATE') {
      notificationType = 'success'
    } else if (order.type === 'CANCELLATION') {
      notificationType = 'warning'
    }

    notification[notificationType]({
      message: title,
      description,
      placement: 'topRight',
      duration: order.type === 'CANCELLATION' ? 3 : 5,
      key: `order-${order.id}`,
    })
  }, [getOrderTypeText, t])

  // Decide whether the app should route to the first-use flow.
  useEffect(() => {
    const checkFirstUse = async () => {
      try {
        const response = await apiService.auth.checkFirstUse()
        if (response.data.code === 0 && response.data.data) {
          setIsFirstUse(response.data.data.isFirstUse)
        }
      } catch (error) {
        console.error('检查首次使用失败:', error)
        setIsFirstUse(false)
      } finally {
        setChecking(false)
      }
    }

    checkFirstUse()
  }, [])

  // Connect WebSocket only after auth and first-use checks are ready.
  useEffect(() => {
    if (!checking && isFirstUse === false && hasToken() && !wsManager.isConnected()) {
      wsManager.connect()
    } else if (!hasToken() && wsManager.isConnected()) {
      wsManager.disconnect()
    }
  }, [checking, isFirstUse])

  // Subscribe once for order push notifications.
  useEffect(() => {
    const unsubscribe = wsManager.subscribe('order', (data: OrderPushMessage) => {
      handleOrderPush(data)
    })

    return () => {
      unsubscribe()
    }
  }, [handleOrderPush])

  // If first-use detection is still running, show the loading state first.
  if (checking) {
    return (
      <ConfigProvider locale={getAntdLocale()}>
        <div style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: '100vh'
        }}>
          <Spin size="large" />
        </div>
      </ConfigProvider>
    )
  }

  // Force the password reset flow on first use.
  if (isFirstUse === true) {
    return (
      <ConfigProvider locale={getAntdLocale()}>
        <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
          <Routes>
            <Route path="/reset-password" element={<LazyRoute><ResetPassword /></LazyRoute>} />
            <Route path="*" element={<Navigate to="/reset-password" replace />} />
          </Routes>
        </BrowserRouter>
      </ConfigProvider>
    )
  }

  return (
    <ConfigProvider locale={getAntdLocale()}>
      <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes>
          <Route path="/login" element={<LazyRoute><Login /></LazyRoute>} />
          <Route path="/reset-password" element={<LazyRoute><ResetPassword /></LazyRoute>} />

          <Route path="/" element={<ProtectedRoute><Navigate to="/accounts" replace /></ProtectedRoute>} />
          <Route path="/accounts" element={<ProtectedRoute><LazyRoute><AccountList /></LazyRoute></ProtectedRoute>} />
          <Route path="/accounts/import" element={<ProtectedRoute><LazyRoute><AccountImport /></LazyRoute></ProtectedRoute>} />
          <Route path="/accounts/detail" element={<ProtectedRoute><LazyRoute><AccountDetail /></LazyRoute></ProtectedRoute>} />
          <Route path="/accounts/edit" element={<ProtectedRoute><LazyRoute><AccountEdit /></LazyRoute></ProtectedRoute>} />
          <Route path="/leaders" element={<ProtectedRoute><LazyRoute><LeaderList /></LazyRoute></ProtectedRoute>} />
          <Route path="/leaders/add" element={<ProtectedRoute><LazyRoute><LeaderAdd /></LazyRoute></ProtectedRoute>} />
          <Route path="/leaders/edit" element={<ProtectedRoute><LazyRoute><LeaderEdit /></LazyRoute></ProtectedRoute>} />
          <Route path="/copy-trading" element={<ProtectedRoute><LazyRoute><CopyTradingList /></LazyRoute></ProtectedRoute>} />
          <Route path="/copy-trading-settings" element={<ProtectedRoute><Navigate to="/copy-trading" replace /></ProtectedRoute>} />
          <Route path="/copy-trading/statistics/:copyTradingId" element={<ProtectedRoute><LazyRoute><CopyTradingStatistics /></LazyRoute></ProtectedRoute>} />
          <Route path="/copy-trading/orders/buy/:copyTradingId" element={<ProtectedRoute><LazyRoute><CopyTradingBuyOrders /></LazyRoute></ProtectedRoute>} />
          <Route path="/copy-trading/orders/sell/:copyTradingId" element={<ProtectedRoute><LazyRoute><CopyTradingSellOrders /></LazyRoute></ProtectedRoute>} />
          <Route path="/copy-trading/orders/matched/:copyTradingId" element={<ProtectedRoute><LazyRoute><CopyTradingMatchedOrders /></LazyRoute></ProtectedRoute>} />
          <Route path="/copy-trading/filtered-orders/:id" element={<ProtectedRoute><LazyRoute><FilteredOrdersList /></LazyRoute></ProtectedRoute>} />
          <Route path="/config" element={<ProtectedRoute><LazyRoute><ConfigPage /></LazyRoute></ProtectedRoute>} />
          <Route path="/positions" element={<ProtectedRoute><LazyRoute><PositionList /></LazyRoute></ProtectedRoute>} />
          <Route path="/market-betting-query" element={<ProtectedRoute><LazyRoute><MarketBettingQuery /></LazyRoute></ProtectedRoute>} />
          <Route path="/large-bet-monitor" element={<ProtectedRoute><LazyRoute><LargeBetMonitor /></LazyRoute></ProtectedRoute>} />
          <Route path="/users" element={<ProtectedRoute><LazyRoute><UserList /></LazyRoute></ProtectedRoute>} />
          <Route path="/announcements" element={<ProtectedRoute><LazyRoute><Announcements /></LazyRoute></ProtectedRoute>} />
          <Route path="/system-settings" element={<ProtectedRoute><LazyRoute><SystemSettings /></LazyRoute></ProtectedRoute>} />
          <Route path="/system-settings/notification" element={<ProtectedRoute><LazyRoute><NotificationSettingsPage /></LazyRoute></ProtectedRoute>} />
          <Route path="/system-settings/rpc-nodes" element={<ProtectedRoute><LazyRoute><RpcNodeSettings /></LazyRoute></ProtectedRoute>} />
          <Route path="/system-settings/api-health" element={<ProtectedRoute><LazyRoute><ApiHealthStatus /></LazyRoute></ProtectedRoute>} />
          <Route path="/system-settings/update" element={<ProtectedRoute><LazyRoute><SystemUpdate /></LazyRoute></ProtectedRoute>} />

          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  )
}

export default App
