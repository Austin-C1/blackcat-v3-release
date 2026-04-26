import React, { useCallback, useEffect, useState } from 'react'
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd'
import {
  CheckOutlined,
  DeleteOutlined,
  EditOutlined,
  FormOutlined,
  PlusOutlined,
  ReloadOutlined,
  RobotOutlined,
  SendOutlined,
} from '@ant-design/icons'
import TextArea from 'antd/es/input/TextArea'
import { useTranslation } from 'react-i18next'
import { useMediaQuery } from 'react-responsive'
import { TelegramConfigForm } from '../components/notifications'
import { apiService } from '../services/api'
import type {
  NotificationConfig,
  NotificationConfigRequest,
  NotificationConfigUpdateRequest,
  NotificationTemplate,
  TemplateTypeInfo,
  TemplateVariable,
  TemplateVariablesResponse,
} from '../types'
import { extractApiErrorMessage } from '../utils/apiError'
import {
  extractTelegramConfig,
  isTelegramConfigReadyForTest,
  normalizeChatIds,
} from './notificationSettingsHelpers'

const { Paragraph, Text, Title } = Typography

const variableTagStyle: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  cursor: 'pointer',
  marginBottom: 6,
  marginRight: 6,
  borderRadius: 6,
  padding: '4px 10px',
  fontSize: 12,
  transition: 'all 0.2s ease',
  border: '1px solid #e8e8e8',
  background: '#ffffff',
  color: 'rgba(0, 0, 0, 0.65)',
}

const variableTagHoverStyle: React.CSSProperties = {
  borderColor: '#1890ff',
  background: '#e6f7ff',
  color: '#1890ff',
  transform: 'translateY(-1px)',
  boxShadow: '0 2px 4px rgba(24, 144, 255, 0.2)',
}

const CATEGORY_LABELS: Record<string, string> = {
  common: 'notificationSettings.templates.commonVariables',
  order: 'notificationSettings.templates.orderVariables',
  copy_trading: 'notificationSettings.templates.copyTradingVariables',
  monitor: 'notificationSettings.templates.monitorVariables',
  redeem: 'notificationSettings.templates.redeemVariables',
  error: 'notificationSettings.templates.errorVariables',
  filter: 'notificationSettings.templates.filterVariables',
  strategy: 'notificationSettings.templates.strategyVariables',
}

const TEST_NOTIFICATION_MESSAGE = '这是一条测试消息'

const NotificationSettingsPage: React.FC = () => {
  const { t } = useTranslation()
  const isMobile = useMediaQuery({ maxWidth: 768 })
  const [form] = Form.useForm()
  const [orderFilterForm] = Form.useForm()
  const [configs, setConfigs] = useState<NotificationConfig[]>([])
  const [loading, setLoading] = useState(false)
  const [orderFilterLoading, setOrderFilterLoading] = useState(false)
  const [orderFilterSaving, setOrderFilterSaving] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [editingConfig, setEditingConfig] = useState<NotificationConfig | null>(null)
  const [testLoading, setTestLoading] = useState(false)
  const [templateTypes, setTemplateTypes] = useState<TemplateTypeInfo[]>([])
  const [selectedTemplateType, setSelectedTemplateType] = useState<string>('ORDER_SUCCESS')
  const [currentTemplate, setCurrentTemplate] = useState<NotificationTemplate | null>(null)
  const [templateVariables, setTemplateVariables] = useState<TemplateVariablesResponse | null>(null)
  const [templateContent, setTemplateContent] = useState('')
  const [testTemplateLoading, setTestTemplateLoading] = useState(false)
  const [variableHoverKey, setVariableHoverKey] = useState<string | null>(null)

  const showApiError = useCallback((error: unknown, fallback: string) => {
    message.error(extractApiErrorMessage(error, fallback))
  }, [])

  const getConfigChatIds = useCallback((config: NotificationConfig) => {
    return normalizeChatIds(extractTelegramConfig(config).chatIds)
  }, [])

  const getMonitorModeEnabled = useCallback((config: NotificationConfig) => {
    return Boolean(extractTelegramConfig(config).monitorModeEnabled)
  }, [])

  const isConfigReadyForTest = useCallback((config: NotificationConfig) => isTelegramConfigReadyForTest(config), [])

  const readyTestConfigs = configs.filter(isConfigReadyForTest)
  const hasReadyTestConfig = readyTestConfigs.length > 0

  const fetchOrderNotificationFilter = useCallback(async () => {
    setOrderFilterLoading(true)
    try {
      const response = await apiService.systemConfig.get()
      if (response.data.code === 0 && response.data.data) {
        const minAmount = Number(response.data.data.orderNotificationMinAmountUsdc || 10)
        orderFilterForm.setFieldsValue({
          orderNotificationMinAmountUsdc: Number.isFinite(minAmount) ? minAmount : 10,
        })
      } else {
        message.error(response.data.msg || t('notificationSettings.orderFilter.fetchFailed'))
      }
    } catch (error) {
      showApiError(error, t('notificationSettings.orderFilter.fetchFailed'))
    } finally {
      setOrderFilterLoading(false)
    }
  }, [orderFilterForm, showApiError, t])

  const fetchConfigs = useCallback(async () => {
    setLoading(true)
    try {
      const response = await apiService.notifications.list({ type: 'telegram' })
      if (response.data.code === 0 && response.data.data) {
        setConfigs(response.data.data)
      } else {
        message.error(response.data.msg || t('notificationSettings.fetchFailed'))
      }
    } catch (error) {
      showApiError(error, t('notificationSettings.fetchFailed'))
    } finally {
      setLoading(false)
    }
  }, [showApiError, t])

  const fetchTemplateTypes = useCallback(async () => {
    try {
      const response = await apiService.notifications.getTemplateTypes()
      if (response.data.code === 0 && response.data.data) {
        setTemplateTypes(response.data.data)
      }
    } catch (error) {
      showApiError(error, t('notificationSettings.templates.fetchTypesFailed'))
    }
  }, [showApiError, t])

  const fetchTemplateDetail = useCallback(async (templateType: string) => {
    try {
      const response = await apiService.notifications.getTemplateDetail({ templateType })
      if (response.data.code === 0 && response.data.data) {
        setCurrentTemplate(response.data.data)
        setTemplateContent(response.data.data.templateContent)
      }
    } catch (error) {
      setCurrentTemplate(null)
      setTemplateContent('')
      showApiError(error, t('notificationSettings.templates.fetchDetailFailed'))
    }
  }, [showApiError, t])

  const fetchTemplateVariables = useCallback(async (templateType: string) => {
    try {
      const response = await apiService.notifications.getTemplateVariables({ templateType })
      if (response.data.code === 0 && response.data.data) {
        setTemplateVariables(response.data.data)
      }
    } catch (error) {
      setTemplateVariables(null)
      showApiError(error, t('notificationSettings.templates.fetchVariablesFailed'))
    }
  }, [showApiError, t])

  useEffect(() => {
    fetchConfigs()
    fetchTemplateTypes()
    fetchOrderNotificationFilter()
  }, [fetchConfigs, fetchOrderNotificationFilter, fetchTemplateTypes])

  useEffect(() => {
    if (!selectedTemplateType) {
      return
    }

    fetchTemplateDetail(selectedTemplateType)
    fetchTemplateVariables(selectedTemplateType)
  }, [fetchTemplateDetail, fetchTemplateVariables, selectedTemplateType])

  const handleCreate = () => {
    setEditingConfig(null)
    form.resetFields()
    form.setFieldsValue({
      type: 'telegram',
      enabled: true,
      config: {
        botToken: '',
        chatIds: '',
        monitorModeEnabled: false,
      },
    })
    setModalVisible(true)
  }

  const handleEdit = (config: NotificationConfig) => {
    setEditingConfig(config)
    const telegramConfig = extractTelegramConfig(config)
    const chatIds = normalizeChatIds(telegramConfig.chatIds).join(',')

    form.setFieldsValue({
      type: config.type,
      name: config.name,
      enabled: config.enabled,
      config: {
        botToken: telegramConfig.botToken || '',
        chatIds,
        monitorModeEnabled: Boolean(telegramConfig.monitorModeEnabled),
      },
    })
    setModalVisible(true)
  }

  const buildConfigPayload = (
    config: NotificationConfig,
    monitorModeEnabled: boolean
  ): NotificationConfigUpdateRequest => {
    const telegramConfig = extractTelegramConfig(config)

    return {
      id: config.id!,
      type: config.type,
      name: config.name,
      enabled: config.enabled,
      config: {
        botToken: telegramConfig.botToken || '',
        chatIds: normalizeChatIds(telegramConfig.chatIds),
        monitorModeEnabled,
      },
    }
  }

  const handleDelete = async (id: number) => {
    try {
      const response = await apiService.notifications.delete({ id })
      if (response.data.code === 0) {
        message.success(t('notificationSettings.deleteSuccess'))
        fetchConfigs()
      } else {
        message.error(response.data.msg || t('notificationSettings.deleteFailed'))
      }
    } catch (error) {
      showApiError(error, t('notificationSettings.deleteFailed'))
    }
  }

  const handleUpdateEnabled = async (id: number, enabled: boolean) => {
    try {
      const response = await apiService.notifications.updateEnabled({ id, enabled })
      if (response.data.code === 0) {
        message.success(enabled ? t('notificationSettings.enableSuccess') : t('notificationSettings.disableSuccess'))
        fetchConfigs()
      } else {
        message.error(response.data.msg || t('notificationSettings.updateStatusFailed'))
      }
    } catch (error) {
      showApiError(error, t('notificationSettings.updateStatusFailed'))
    }
  }

  const handleToggleMonitorMode = async (config: NotificationConfig, monitorModeEnabled: boolean) => {
    try {
      const response = await apiService.notifications.update(buildConfigPayload(config, monitorModeEnabled))
      if (response.data.code === 0) {
        message.success(
          monitorModeEnabled
            ? t('notificationSettings.monitorModeEnableSuccess')
            : t('notificationSettings.monitorModeDisableSuccess')
        )
        fetchConfigs()
      } else {
        message.error(response.data.msg || t('notificationSettings.monitorModeUpdateFailed'))
      }
    } catch (error) {
      showApiError(error, t('notificationSettings.monitorModeUpdateFailed'))
    }
  }

  const handleTestConfig = async (config: NotificationConfig) => {
    if (!isConfigReadyForTest(config)) {
      message.warning(t('notificationSettings.testUnavailable'))
      return
    }

    setTestLoading(true)
    try {
      const response = await apiService.notifications.test({
        configId: config.id,
        message: TEST_NOTIFICATION_MESSAGE,
      })
      if (response.data.code === 0 && response.data.data) {
        message.success(t('notificationSettings.testSuccess'))
      } else {
        message.error(response.data.msg || t('notificationSettings.testFailed'))
      }
    } catch (error) {
      showApiError(error, t('notificationSettings.testFailed'))
    } finally {
      setTestLoading(false)
    }
  }

  const handleSaveOrderNotificationFilter = async () => {
    try {
      const values = await orderFilterForm.validateFields()
      const minAmount = values.orderNotificationMinAmountUsdc ?? 0

      setOrderFilterSaving(true)
      const response = await apiService.systemConfig.updateOrderNotificationMinAmount({
        minAmountUsdc: String(minAmount),
      })

      if (response.data.code === 0 && response.data.data) {
        const savedAmount = Number(response.data.data.orderNotificationMinAmountUsdc || minAmount)
        orderFilterForm.setFieldsValue({
          orderNotificationMinAmountUsdc: Number.isFinite(savedAmount) ? savedAmount : minAmount,
        })
        message.success(t('notificationSettings.orderFilter.saveSuccess'))
      } else {
        message.error(response.data.msg || t('notificationSettings.orderFilter.saveFailed'))
      }
    } catch (error: any) {
      if (error?.errorFields) {
        return
      }
      showApiError(error, t('notificationSettings.orderFilter.saveFailed'))
    } finally {
      setOrderFilterSaving(false)
    }
  }

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields()
      const configData: NotificationConfigRequest | NotificationConfigUpdateRequest = {
        type: values.type,
        name: values.name,
        enabled: values.enabled,
        config: {
          botToken: values.config.botToken,
          chatIds: normalizeChatIds(values.config.chatIds),
          monitorModeEnabled: Boolean(values.config.monitorModeEnabled),
        },
      }

      if (editingConfig?.id) {
        const response = await apiService.notifications.update({
          ...(configData as NotificationConfigRequest),
          id: editingConfig.id,
        })
        if (response.data.code === 0) {
          message.success(t('notificationSettings.updateSuccess'))
          setModalVisible(false)
          fetchConfigs()
        } else {
          message.error(response.data.msg || t('notificationSettings.updateFailed'))
        }
      } else {
        const response = await apiService.notifications.create(configData as NotificationConfigRequest)
        if (response.data.code === 0) {
          message.success(t('notificationSettings.createSuccess'))
          setModalVisible(false)
          fetchConfigs()
        } else {
          message.error(response.data.msg || t('notificationSettings.createFailed'))
        }
      }
    } catch (error: any) {
      if (error?.errorFields) {
        return
      }
      showApiError(error, t('message.error'))
    }
  }

  const handleSaveTemplate = async () => {
    try {
      const response = await apiService.notifications.updateTemplate({
        templateType: selectedTemplateType,
        templateContent,
      })
      if (response.data.code === 0) {
        message.success(t('notificationSettings.templates.saveSuccess'))
        fetchTemplateDetail(selectedTemplateType)
      } else {
        message.error(response.data.msg || t('notificationSettings.templates.saveFailed'))
      }
    } catch (error) {
      showApiError(error, t('notificationSettings.templates.saveFailed'))
    }
  }

  const handleResetTemplate = async () => {
    try {
      const response = await apiService.notifications.resetTemplate({
        templateType: selectedTemplateType,
      })
      if (response.data.code === 0) {
        message.success(t('notificationSettings.templates.resetSuccess'))
        fetchTemplateDetail(selectedTemplateType)
      } else {
        message.error(response.data.msg || t('notificationSettings.templates.resetFailed'))
      }
    } catch (error) {
      showApiError(error, t('notificationSettings.templates.resetFailed'))
    }
  }

  const handleTestTemplate = async () => {
    if (!hasReadyTestConfig) {
      message.warning(t('notificationSettings.testUnavailable'))
      return
    }

    setTestTemplateLoading(true)
    try {
      const response = await apiService.notifications.testTemplate({
        templateType: selectedTemplateType,
        templateContent,
      })
      if (response.data.code === 0 && response.data.data) {
        message.success(t('notificationSettings.templates.testSuccess'))
      } else {
        message.error(response.data.msg || t('notificationSettings.templates.testFailed'))
      }
    } catch (error) {
      showApiError(error, t('notificationSettings.templates.testFailed'))
    } finally {
      setTestTemplateLoading(false)
    }
  }

  const handleCopyVariable = useCallback((variable: string) => {
    const text = `{{${variable}}}`

    const fallbackCopy = (value: string) => {
      const textArea = document.createElement('textarea')
      textArea.value = value
      textArea.style.position = 'fixed'
      textArea.style.left = '-9999px'
      textArea.style.top = '-9999px'
      document.body.appendChild(textArea)
      textArea.focus()
      textArea.select()

      try {
        document.execCommand('copy')
        message.success(t('notificationSettings.templates.copied'))
      } catch {
        message.error(t('common.copyFailed'))
      } finally {
        document.body.removeChild(textArea)
      }
    }

    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard
        .writeText(text)
        .then(() => {
          message.success(t('notificationSettings.templates.copied'))
        })
        .catch(() => fallbackCopy(text))
      return
    }

    fallbackCopy(text)
  }, [t])

  const renderVariableItem = (variable: TemplateVariable) => {
    const isHover = variableHoverKey === variable.key
    const label = t(`notificationSettings.templates.variableLabels.${variable.key}`)
    const description = t(`notificationSettings.templates.variableDescriptions.${variable.key}`)

    const content = (
      <span
        role="button"
        tabIndex={0}
        style={{ ...variableTagStyle, ...(isHover && !isMobile ? variableTagHoverStyle : {}) }}
        onClick={() => handleCopyVariable(variable.key)}
        onMouseEnter={() => !isMobile && setVariableHoverKey(variable.key)}
        onMouseLeave={() => !isMobile && setVariableHoverKey(null)}
        onKeyDown={(event) => event.key === 'Enter' && handleCopyVariable(variable.key)}
      >
        <span style={{ fontFamily: 'monospace' }}>{label}</span>
      </span>
    )

    if (isMobile) {
      return (
        <span key={variable.key} style={{ display: 'inline-block' }}>
          {content}
        </span>
      )
    }

    return (
      <Tooltip key={variable.key} title={description || `{{${variable.key}}}`} placement="top">
        {content}
      </Tooltip>
    )
  }

  const renderVariablesPanel = () => {
    if (!templateVariables) {
      return null
    }

    return (
      <Card
        size="small"
        title={<span style={{ fontSize: 13, fontWeight: 500 }}>{t('notificationSettings.templates.variables')}</span>}
        style={{ height: '100%', borderRadius: 8 }}
        styles={{ body: { padding: '12px 16px', maxHeight: 420, overflowY: 'auto' } }}
      >
        {templateVariables.categories.map((category) => {
          const categoryVariables = templateVariables.variables.filter((item) => item.category === category.key)
          if (categoryVariables.length === 0) {
            return null
          }

          return (
            <div key={category.key} style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ marginBottom: 8, display: 'block', fontSize: 12 }}>
                {t(CATEGORY_LABELS[category.key])}
              </Text>
              <div style={{ display: 'flex', flexWrap: 'wrap' }}>
                {categoryVariables.sort((a, b) => a.sortOrder - b.sortOrder).map(renderVariableItem)}
              </div>
            </div>
          )
        })}
        <Paragraph type="secondary" style={{ marginTop: 12, marginBottom: 0, fontSize: 11, textAlign: 'center' }}>
          {t('notificationSettings.templates.clickToCopy')}
        </Paragraph>
      </Card>
    )
  }

  const templateTypeTabItems = templateTypes.map((type) => ({
    key: type.type,
    label: (
      <Tooltip title={t(`notificationSettings.templateTypeDescriptions.${type.type}`)} placement="top">
        <span>{t(`notificationSettings.templateTypes.${type.type}`)}</span>
      </Tooltip>
    ),
  }))

  const configColumns = [
    {
      title: t('notificationSettings.configName'),
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: t('notificationSettings.type'),
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => <Tag color="blue">{type.toUpperCase()}</Tag>,
    },
    {
      title: t('notificationSettings.status'),
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'green' : 'default'}>
          {enabled ? t('notificationSettings.enabledStatus') : t('notificationSettings.disabledStatus')}
        </Tag>
      ),
    },
    {
      title: t('notificationSettings.monitorMode'),
      key: 'monitorMode',
      render: (_: unknown, record: NotificationConfig) => (
        <Tooltip title={t('notificationSettings.monitorModeDescription')}>
          <Switch
            checked={getMonitorModeEnabled(record)}
            size="small"
            onChange={(checked) => handleToggleMonitorMode(record, checked)}
          />
        </Tooltip>
      ),
    },
    {
      title: t('notificationSettings.chatIds'),
      key: 'chatIds',
      render: (_: unknown, record: NotificationConfig) => {
        const chatIds = getConfigChatIds(record)
        return chatIds.length > 0 ? (
          <Text type="secondary" style={{ fontSize: 12 }}>
            {chatIds.join(', ')}
          </Text>
        ) : (
          <Text type="danger" style={{ fontSize: 12 }}>
            {t('notificationSettings.chatIdsNotConfigured')}
          </Text>
        )
      },
    },
    {
      title: t('common.actions'),
      key: 'action',
      width: isMobile ? 140 : 220,
      render: (_: unknown, record: NotificationConfig) => (
        <Space size="small" wrap>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            {t('notificationSettings.edit')}
          </Button>
          <Switch checked={record.enabled} size="small" onChange={(checked) => handleUpdateEnabled(record.id!, checked)} />
          <Tooltip title={!isConfigReadyForTest(record) ? t('notificationSettings.testUnavailable') : undefined}>
            <span>
              <Button
                type="link"
                size="small"
                icon={<SendOutlined />}
                loading={testLoading}
                disabled={!isConfigReadyForTest(record)}
                onClick={() => handleTestConfig(record)}
              >
                {t('notificationSettings.test')}
              </Button>
            </span>
          </Tooltip>
          <Popconfirm
            title={t('notificationSettings.deleteConfirm')}
            onConfirm={() => handleDelete(record.id!)}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
          >
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>
              {t('notificationSettings.delete')}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>
          {t('notificationSettings.title')}
        </Title>
      </div>

      <Card
        title={t('notificationSettings.orderFilter.title')}
        style={{ marginBottom: 16 }}
        loading={orderFilterLoading}
      >
        <Form form={orderFilterForm} layout={isMobile ? 'vertical' : 'inline'}>
          <Form.Item
            name="orderNotificationMinAmountUsdc"
            label={t('notificationSettings.orderFilter.minAmount')}
            rules={[{ required: true, message: t('notificationSettings.orderFilter.minAmountRequired') }]}
          >
            <InputNumber min={0} precision={2} addonAfter="USDC" style={{ width: isMobile ? '100%' : 220 }} />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              icon={<CheckOutlined />}
              loading={orderFilterSaving}
              onClick={handleSaveOrderNotificationFilter}
            >
              {t('common.save')}
            </Button>
          </Form.Item>
        </Form>
        <Paragraph type="secondary" style={{ marginBottom: 0 }}>
          {t('notificationSettings.orderFilter.description')}
        </Paragraph>
      </Card>

      <Card
        title={
          <Space>
            <RobotOutlined />
            <span>{t('notificationSettings.botConfig')}</span>
          </Space>
        }
        style={{ marginBottom: 16 }}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            {t('notificationSettings.addConfig')}
          </Button>
        }
      >
        <Table
          columns={configColumns}
          dataSource={configs}
          loading={loading}
          rowKey="id"
          pagination={false}
          scroll={{ x: isMobile ? 760 : 'auto' }}
        />
      </Card>

      <Card
        title={
          <Space>
            <FormOutlined />
            <span>{t('notificationSettings.templateConfig')}</span>
          </Space>
        }
      >
        <Tabs
          activeKey={selectedTemplateType}
          onChange={setSelectedTemplateType}
          items={templateTypeTabItems}
          style={{ marginBottom: 16 }}
          tabBarStyle={{ marginBottom: 0 }}
          type={isMobile ? 'line' : 'card'}
          size={isMobile ? 'small' : 'middle'}
        />

        <Row gutter={[16, 16]}>
          <Col xs={24} sm={24} md={17}>
            <Card
              size="small"
              variant="borderless"
              style={{ background: '#fafafa', marginBottom: 12, borderRadius: 8 }}
              styles={{ body: { padding: '10px 16px' } }}
            >
              <div
                style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: 8,
                  alignItems: 'center',
                  justifyContent: 'space-between',
                }}
              >
                <Space wrap size="small">
                  <Text type="secondary" style={{ fontSize: 13 }}>
                    {t('notificationSettings.templates.templateContent')}
                  </Text>
                  {currentTemplate && (
                    <Tag color={currentTemplate.isDefault ? 'green' : 'blue'} style={{ margin: 0 }}>
                      {currentTemplate.isDefault
                        ? t('notificationSettings.templates.isDefault')
                        : t('notificationSettings.templates.isCustom')}
                    </Tag>
                  )}
                </Space>
                <Space wrap size="small">
                  <Popconfirm
                    title={t('notificationSettings.templates.resetConfirm')}
                    onConfirm={handleResetTemplate}
                    okText={t('common.confirm')}
                    cancelText={t('common.cancel')}
                  >
                    <Button size="small" icon={<ReloadOutlined />}>
                      {t('notificationSettings.templates.resetToDefault')}
                    </Button>
                  </Popconfirm>
                  <Button size="small" type="primary" icon={<CheckOutlined />} onClick={handleSaveTemplate}>
                    {t('common.save')}
                  </Button>
                  <Tooltip title={!hasReadyTestConfig ? t('notificationSettings.testUnavailable') : undefined}>
                    <span>
                      <Button
                        size="small"
                        icon={<SendOutlined />}
                        loading={testTemplateLoading}
                        disabled={!hasReadyTestConfig}
                        onClick={handleTestTemplate}
                      >
                        {t('notificationSettings.test')}
                      </Button>
                    </span>
                  </Tooltip>
                </Space>
              </div>
            </Card>
            <TextArea
              value={templateContent}
              onChange={(event) => setTemplateContent(event.target.value)}
              rows={isMobile ? 15 : 16}
              style={{ fontFamily: 'monospace', fontSize: 13, borderRadius: 8, resize: 'none' }}
              placeholder={t('notificationSettings.templates.contentPlaceholder')}
            />
          </Col>
          <Col xs={24} sm={24} md={7}>
            {renderVariablesPanel()}
          </Col>
        </Row>
      </Card>

      <Modal
        title={editingConfig ? t('notificationSettings.editConfig') : t('notificationSettings.addConfig')}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={isMobile ? '90%' : 600}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="type"
            label={t('notificationSettings.type')}
            rules={[{ required: true, message: t('notificationSettings.typeRequired') }]}
          >
            <Input disabled value="telegram" />
          </Form.Item>
          <Form.Item
            name="name"
            label={t('notificationSettings.configName')}
            rules={[{ required: true, message: t('notificationSettings.configNameRequired') }]}
          >
            <Input placeholder={t('notificationSettings.configNamePlaceholder')} />
          </Form.Item>
          <Form.Item name="enabled" label={t('notificationSettings.enabled')} valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name={['config', 'monitorModeEnabled']} hidden>
            <Input />
          </Form.Item>
          <Form.Item shouldUpdate={(prevValues, currentValues) => prevValues.type !== currentValues.type}>
            {() => {
              const currentType = form.getFieldValue('type') || 'telegram'
              if (currentType !== 'telegram') {
                return null
              }
              return <TelegramConfigForm form={form} />
            }}
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default NotificationSettingsPage
