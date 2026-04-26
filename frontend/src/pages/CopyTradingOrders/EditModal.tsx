import { useEffect, useState } from 'react'
import { Alert, Button, Form, Input, Modal, Select, Space, Switch, message } from 'antd'
import { useMediaQuery } from 'react-responsive'
import { apiService } from '../../services/api'
import type { CopyTrading, CopyTradingEditFormValues, CopyTradingUpdateRequest, NotificationConfig } from '../../types'

interface EditModalProps {
  open: boolean
  onClose: () => void
  copyTradingId: string
  onSuccess?: () => void
}

const EditModal: React.FC<EditModalProps> = ({ open, onClose, copyTradingId, onSuccess }) => {
  const isMobile = useMediaQuery({ maxWidth: 768 })
  const [form] = Form.useForm<CopyTradingEditFormValues>()
  const [loading, setLoading] = useState(false)
  const [fetching, setFetching] = useState(false)
  const [copyTrading, setCopyTrading] = useState<CopyTrading | null>(null)
  const [telegramConfigs, setTelegramConfigs] = useState<NotificationConfig[]>([])

  useEffect(() => {
    if (!open || !copyTradingId) {
      return
    }
    fetchCopyTrading(Number(copyTradingId))
    fetchTelegramConfigs()
  }, [open, copyTradingId])

  const fetchTelegramConfigs = async () => {
    try {
      const response = await apiService.notifications.list({ type: 'telegram' })
      if (response.data.code === 0 && response.data.data) {
        setTelegramConfigs(response.data.data.filter((item) => item.type.toLowerCase() === 'telegram'))
      }
    } catch (error: any) {
      message.error(error.message || '获取机器人列表失败')
    }
  }

  const fetchCopyTrading = async (targetId: number) => {
    setFetching(true)
    try {
      const response = await apiService.copyTrading.detail({ copyTradingId: targetId })
      if (response.data.code === 0 && response.data.data) {
        const target = response.data.data
        setCopyTrading(target)
        form.setFieldsValue({
          configName: target.configName,
          supportSell: target.supportSell,
          pushFailedOrders: target.pushFailedOrders,
          pushFilteredOrders: target.pushFilteredOrders,
          notificationRoutes: target.notificationRoutes || [],
        })
      } else {
        message.error(response.data.msg || '获取跟单配置失败')
        onClose()
      }
    } catch (error: any) {
      message.error(error.message || '获取跟单配置失败')
      onClose()
    } finally {
      setFetching(false)
    }
  }

  const handleSubmit = async (values: CopyTradingEditFormValues) => {
    if (!copyTrading) {
      return
    }

    setLoading(true)
    try {
      const request: CopyTradingUpdateRequest = {
        copyTradingId: copyTrading.id,
        enabled: copyTrading.enabled,
        configName: values.configName?.trim(),
        supportSell: values.supportSell,
        pushFailedOrders: values.pushFailedOrders,
        pushFilteredOrders: values.pushFilteredOrders,
        notificationRoutes: values.notificationRoutes || [],
      }

      const response = await apiService.copyTrading.update(request)
      if (response.data.code === 0) {
        message.success('跟单配置已更新')
        onClose()
        onSuccess?.()
      } else {
        message.error(response.data.msg || '更新跟单配置失败')
      }
    } catch (error: any) {
      message.error(error.message || '更新跟单配置失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      title="编辑跟单"
      open={open}
      onCancel={onClose}
      footer={null}
      width={isMobile ? '94%' : 560}
      destroyOnHidden
      forceRender
    >
      {fetching || !copyTrading ? (
        <div style={{ padding: '48px 0', textAlign: 'center' }}>加载中...</div>
      ) : (
        <>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="这里只修改当前跟单的基础设置，账户、Leader 和其他策略参数保持不变。"
          />

          <Form form={form} layout="vertical" onFinish={handleSubmit}>
            <Form.Item
              label="配置名称"
              name="configName"
              rules={[
                { required: true, message: '请输入配置名称' },
                { whitespace: true, message: '配置名称不能为空' },
              ]}
            >
              <Input maxLength={255} />
            </Form.Item>

            <Form.Item label="账户">
              <Select
                disabled
                value={copyTrading.accountId}
                options={[
                  {
                    value: copyTrading.accountId,
                    label: `${copyTrading.accountName || `账户 ${copyTrading.accountId}`} (${copyTrading.walletAddress.slice(0, 6)}...${copyTrading.walletAddress.slice(-4)})`,
                  },
                ]}
              />
            </Form.Item>

            <Form.Item label="Leader">
              <Select
                disabled
                value={copyTrading.leaderId}
                options={[
                  {
                    value: copyTrading.leaderId,
                    label: `${copyTrading.leaderName || `Leader ${copyTrading.leaderId}`} (${copyTrading.leaderAddress.slice(0, 6)}...${copyTrading.leaderAddress.slice(-4)})`,
                  },
                ]}
              />
            </Form.Item>

            <Form.Item label="允许跟卖" name="supportSell" valuePropName="checked">
              <Switch checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>

            <Form.Item label="推送失败订单" name="pushFailedOrders" valuePropName="checked">
              <Switch checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>

            <Form.Item label="推送已过滤订单" name="pushFilteredOrders" valuePropName="checked">
              <Switch checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>

            <Form.List name="notificationRoutes">
              {(fields, { add, remove }) => (
                <Form.Item label="消息筛选">
                  <Space direction="vertical" style={{ width: '100%' }}>
                    {fields.map((field) => (
                      <Space key={field.key} wrap align="baseline">
                        <Form.Item name={[field.name, 'telegramConfigId']} rules={[{ required: true, message: '请选择机器人' }]} style={{ minWidth: 180 }}>
                          <Select placeholder="选择机器人" options={telegramConfigs.map((item) => ({ label: item.name, value: item.id }))} />
                        </Form.Item>
                        <Form.Item name={[field.name, 'categories']} style={{ minWidth: 180 }}>
                          <Select
                            mode="multiple"
                            allowClear
                            placeholder="盘口类型"
                            options={[
                              { label: '全部', value: 'all' },
                              { label: '体育', value: 'sports' },
                              { label: '加密', value: 'crypto' },
                            ]}
                          />
                        </Form.Item>
                        <Form.Item name={[field.name, 'notificationTypes']} style={{ minWidth: 220 }}>
                          <Select
                            mode="multiple"
                            allowClear
                            placeholder="消息类型"
                            options={[
                              { label: '全部', value: 'all' },
                              { label: '成功订单', value: 'success' },
                              { label: '失败订单', value: 'failed' },
                              { label: '过滤订单', value: 'filtered' },
                              { label: '监控提醒', value: 'monitor' },
                            ]}
                          />
                        </Form.Item>
                        <Button danger onClick={() => remove(field.name)}>删除</Button>
                      </Space>
                    ))}
                    <Button onClick={() => add({ categories: [], notificationTypes: [] })}>添加机器人筛选</Button>
                  </Space>
                </Form.Item>
              )}
            </Form.List>

            <Form.Item style={{ marginBottom: 0 }}>
              <Space>
                <Button type="primary" htmlType="submit" loading={loading}>
                  保存
                </Button>
                <Button onClick={onClose}>取消</Button>
              </Space>
            </Form.Item>
          </Form>
        </>
      )}
    </Modal>
  )
}

export default EditModal
