import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Layout as AntLayout, Menu, Drawer, Button, Modal, Tag } from 'antd'
import type { MenuProps } from 'antd'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useMediaQuery } from 'react-responsive'
import {
  ApiOutlined,
  AppstoreOutlined,
  AlertOutlined,
  CheckCircleOutlined,
  LinkOutlined,
  LogoutOutlined,
  MenuOutlined,
  NotificationOutlined,
  SettingOutlined,
  TeamOutlined,
  UnorderedListOutlined,
  UserOutlined,
  WalletOutlined,
} from '@ant-design/icons'
import { removeToken, getVersionInfo, getVersionText } from '../utils'
import { wsManager } from '../services/websocket'
import Logo from './Logo'

const { Header, Content, Sider } = AntLayout

const style = document.createElement('style')
style.textContent = `
  @keyframes versionUpdatePulse {
    0%, 100% {
      opacity: 1;
      transform: scale(1);
    }
    50% {
      opacity: 0.7;
      transform: scale(1.1);
    }
  }
`

if (!document.head.querySelector('style[data-version-update-animation]')) {
  style.setAttribute('data-version-update-animation', 'true')
  document.head.appendChild(style)
}

interface LayoutProps {
  children: ReactNode
}

const Layout: React.FC<LayoutProps> = ({ children }) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const isMobile = useMediaQuery({ maxWidth: 768 })
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [openKeys, setOpenKeys] = useState<string[]>([])

  const getSelectedKeys = (): string[] => [location.pathname]

  const getInitialOpenKeys = (): string[] => {
    const path = location.pathname
    const keys: string[] = []

    if (path.startsWith('/copy-trading') || path.startsWith('/leaders')) {
      keys.push('/copy-trading-management')
    }

    if (path.startsWith('/system-settings') || path.startsWith('/market-betting-query')) {
      keys.push('/system-settings-management')
    }

    return keys
  }

  useEffect(() => {
    setOpenKeys(getInitialOpenKeys())
  }, [location.pathname])

  const menuItems: MenuProps['items'] = [
    {
      key: '/announcements',
      icon: <NotificationOutlined />,
      label: t('menu.announcements') || '公告',
    },
    {
      key: '/accounts',
      icon: <WalletOutlined />,
      label: t('menu.accounts') || '账户管理',
    },
    {
      key: '/copy-trading-management',
      icon: <AppstoreOutlined />,
      label: t('menu.copyTrading') || '跟单交易',
      children: [
        {
          key: '/copy-trading',
          icon: <LinkOutlined />,
          label: t('menu.copyTradingConfig') || '跟单配置',
        },
        {
          key: '/leaders',
          icon: <UserOutlined />,
          label: t('menu.leaders') || 'Leader 管理',
        },
      ],
    },
    {
      key: '/positions',
      icon: <UnorderedListOutlined />,
      label: t('menu.positions') || '仓位管理',
    },
    {
      key: '/large-bet-monitor',
      icon: <AlertOutlined />,
      label: t('menu.largeBetMonitor') || '大额投注监控',
    },
    {
      key: '/users',
      icon: <TeamOutlined />,
      label: t('menu.users') || '用户管理',
    },
    {
      key: '/system-settings-management',
      icon: <SettingOutlined />,
      label: t('menu.systemSettings') || '系统管理',
      children: [
        {
          key: '/system-settings',
          icon: <SettingOutlined />,
          label: t('menu.systemOverview') || '概览',
        },
        {
          key: '/system-settings/notification',
          icon: <NotificationOutlined />,
          label: t('menu.notifications') || '消息通知',
        },
        {
          key: '/market-betting-query',
          icon: <NotificationOutlined />,
          label: '盘口投注额查询',
        },
        {
          key: '/system-settings/rpc-nodes',
          icon: <ApiOutlined />,
          label: t('menu.rpcNodes') || 'RPC 节点管理',
        },
        {
          key: '/system-settings/api-health',
          icon: <CheckCircleOutlined />,
          label: t('menu.apiHealth') || 'API 健康',
        },
      ],
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: t('menu.logout') || '退出登录',
    },
  ]

  const handleLogout = () => {
    removeToken()
    wsManager.disconnect()
    navigate('/login', { replace: true })
  }

  const handleLogoutConfirm = () => {
    Modal.confirm({
      title: t('menu.logoutConfirm') || '确认退出',
      content: t('menu.logoutConfirmDesc') || '退出后需要重新登录。',
      okText: t('common.confirm') || '确认',
      cancelText: t('common.cancel') || '取消',
      onOk: () => {
        handleLogout()
        if (isMobile) {
          setMobileMenuOpen(false)
        }
      },
    })
  }

  const handleMenuClick = ({ key }: { key: string }) => {
    if (key === '/copy-trading-management' || key === '/system-settings-management') {
      return
    }

    if (key === 'logout') {
      handleLogoutConfirm()
      return
    }

    navigate(key)
    if (isMobile) {
      setMobileMenuOpen(false)
    }
  }

  const renderBrand = () => (
    <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
      <Logo size="normal" darkMode={true} iconOnly />
      <span
        style={{
          color: '#fff',
          fontSize: '18px',
          fontWeight: 700,
          lineHeight: 1,
        }}
      >
        黑猫V3
      </span>
      <Tag
        color="success"
        style={{
          cursor: 'default',
          fontSize: '8px',
          padding: '1px 6px',
          margin: 0,
          background: 'transparent',
          border: '1px solid #52c41a',
          borderRadius: '4px',
          color: '#52c41a',
          lineHeight: '1.4',
          display: 'inline-flex',
          alignItems: 'center',
          verticalAlign: 'middle',
        }}
        title={t('systemUpdate.versionTooltipLatest')}
      >
        {getVersionInfo().gitTag || `v${getVersionText()}`}
      </Tag>
    </div>
  )

  if (isMobile) {
    return (
      <AntLayout style={{ minHeight: '100vh' }}>
        <Header
          style={{
            background: '#001529',
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          {renderBrand()}
          <Button
            type="text"
            icon={<MenuOutlined />}
            style={{ color: '#fff', marginLeft: '4px' }}
            onClick={() => setMobileMenuOpen(true)}
          />
        </Header>
        <Content
          style={{
            padding: '12px 8px',
            background: '#f0f2f5',
            minHeight: 'calc(100vh - 64px)',
          }}
        >
          {children}
        </Content>
        <Drawer
          title={t('menu.navigation') || '导航'}
          placement="left"
          onClose={() => setMobileMenuOpen(false)}
          open={mobileMenuOpen}
          styles={{ body: { padding: 0 } }}
        >
          <Menu
            mode="inline"
            selectedKeys={getSelectedKeys()}
            openKeys={openKeys}
            onOpenChange={setOpenKeys}
            items={menuItems}
            onClick={handleMenuClick}
            style={{ border: 'none' }}
          />
        </Drawer>
      </AntLayout>
    )
  }

  return (
    <AntLayout style={{ height: '100vh', overflow: 'hidden' }}>
      <Sider
        width={200}
        style={{
          background: '#001529',
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            padding: '16px',
            color: '#fff',
            flexShrink: 0,
            borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
          }}
        >
          <div
            style={{
              fontSize: '18px',
              fontWeight: 'bold',
              marginBottom: '12px',
              textAlign: 'center',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '6px',
            }}
          >
            <Logo size="small" darkMode={true} iconOnly />
            <span>黑猫V3</span>
            <Tag
              color="success"
              style={{
                cursor: 'default',
                fontSize: '8px',
                padding: '1px 6px',
                margin: 0,
                background: 'transparent',
                border: '1px solid #52c41a',
                borderRadius: '4px',
                color: '#52c41a',
                lineHeight: '1.4',
                display: 'inline-flex',
                alignItems: 'center',
                verticalAlign: 'middle',
              }}
              title={t('systemUpdate.versionTooltipLatest')}
            >
              {getVersionInfo().gitTag || `v${getVersionText()}`}
            </Tag>
          </div>
        </div>
        <Menu
          mode="inline"
          selectedKeys={getSelectedKeys()}
          openKeys={openKeys}
          onOpenChange={setOpenKeys}
          items={menuItems}
          onClick={handleMenuClick}
          style={{
            height: 'calc(100vh - 100px)',
            borderRight: 0,
            overflowY: 'auto',
          }}
        />
      </Sider>
      <AntLayout style={{ marginLeft: 200, height: '100vh' }}>
        <Content
          style={{
            padding: '24px',
            background: '#f0f2f5',
            height: '100vh',
            overflowY: 'auto',
          }}
        >
          {children}
        </Content>
      </AntLayout>
    </AntLayout>
  )
}

export default Layout
