import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Alert,
  Button,
  Card,
  Divider,
  Empty,
  InputNumber,
  Select,
  Space,
  Switch,
  message,
} from 'antd'
import { ClearOutlined, DeleteOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons'
import { apiService } from '../services/api'
import type { CopyTrading, FollowAmountRule, FollowSettingsSaveItem } from '../types'

interface RuleRow extends FollowAmountRule {
  rowKey: string
}

const createRuleRow = (rule?: Partial<FollowAmountRule>): RuleRow => ({
  rowKey: `${Date.now()}-${Math.random()}`,
  id: rule?.id,
  minLeaderAmount: rule?.minLeaderAmount || '',
  maxLeaderAmount: rule?.maxLeaderAmount || '',
  followAmount: rule?.followAmount || '',
  followMaxAmount: rule?.followMaxAmount || '',
  sortOrder: rule?.sortOrder || 0,
})

const FollowSettings: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams()
  const [copyTradings, setCopyTradings] = useState<CopyTrading[]>([])
  const [selectedCopyTradingId, setSelectedCopyTradingId] = useState<number | undefined>(undefined)
  const [enabled, setEnabled] = useState(true)
  const [rules, setRules] = useState<RuleRow[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    fetchCopyTradings()
  }, [])

  useEffect(() => {
    if (selectedCopyTradingId) {
      fetchDetail(selectedCopyTradingId)
    }
  }, [selectedCopyTradingId])

  const fetchCopyTradings = async () => {
    setLoading(true)
    try {
      const response = await apiService.copyTrading.list({})
      if (response.data.code === 0 && response.data.data) {
        const list = response.data.data.list || []
        setCopyTradings(list)

        if (list.length === 0) {
          setSelectedCopyTradingId(undefined)
          return
        }

        const queryId = Number(searchParams.get('copyTradingId'))
        const initialId = list.some((item: CopyTrading) => item.id === queryId) ? queryId : list[0].id
        setSelectedCopyTradingId(initialId)
        setSearchParams({ copyTradingId: String(initialId) })
      } else {
        message.error(response.data.msg || '获取跟单配置失败')
      }
    } catch (error: any) {
      message.error(error.message || '获取跟单配置失败')
    } finally {
      setLoading(false)
    }
  }

  const fetchDetail = async (copyTradingId: number) => {
    setLoading(true)
    try {
      const response = await apiService.copyTrading.followSettingsDetail({ copyTradingId })
      if (response.data.code === 0 && response.data.data) {
        setEnabled(response.data.data.enabled)
        setRules((response.data.data.rules || []).map((rule) => createRuleRow(rule)))
      } else {
        message.error(response.data.msg || '获取跟单设置失败')
      }
    } catch (error: any) {
      message.error(error.message || '获取跟单设置失败')
    } finally {
      setLoading(false)
    }
  }

  const updateRule = (rowKey: string, field: keyof RuleRow, value: string | null) => {
    setRules((current) =>
      current.map((rule) => (rule.rowKey === rowKey ? { ...rule, [field]: value ?? '' } : rule)),
    )
  }

  const addRule = () => {
    setRules((current) => [...current, createRuleRow({ sortOrder: current.length })])
  }

  const removeRule = (rowKey: string) => {
    setRules((current) =>
      current.filter((rule) => rule.rowKey !== rowKey).map((rule, index) => ({ ...rule, sortOrder: index })),
    )
  }

  const clearRules = () => {
    setRules([])
  }

  const validateRules = (): FollowSettingsSaveItem[] | null => {
    if (!enabled) {
      return []
    }

    if (rules.length === 0) {
      message.error('启用规则时，至少需要保留一条范围')
      return null
    }

    return rules.map((rule, index) => {
      const minLeaderAmount = `${rule.minLeaderAmount ?? ''}`.trim()
      const maxLeaderAmount = `${rule.maxLeaderAmount ?? ''}`.trim()
      const followAmount = `${rule.followAmount ?? ''}`.trim()
      const followMaxAmount = `${rule.followMaxAmount ?? ''}`.trim()

      if (!minLeaderAmount || !followAmount || !followMaxAmount) {
        throw new Error(`第 ${index + 1} 条规则有必填项未填写`)
      }

      const minValue = Number(minLeaderAmount)
      const maxValue = maxLeaderAmount ? Number(maxLeaderAmount) : undefined
      const followValue = Number(followAmount)
      const followMaxValue = Number(followMaxAmount)

      if ([minValue, followValue, followMaxValue].some((value) => Number.isNaN(value) || value < 0)) {
        throw new Error(`第 ${index + 1} 条规则存在无效金额`)
      }

      if (maxValue !== undefined && (Number.isNaN(maxValue) || maxValue <= minValue)) {
        throw new Error(`第 ${index + 1} 条规则的结束金额必须大于起始金额`)
      }

      if (followMaxValue < followValue) {
        throw new Error(`第 ${index + 1} 条规则的单笔上限不能小于投入金额`)
      }

      return {
        minLeaderAmount,
        maxLeaderAmount: maxLeaderAmount || null,
        followAmount,
        followMaxAmount,
      }
    })
  }

  const handleSave = async () => {
    if (!selectedCopyTradingId) {
      return
    }

    let normalizedRules: FollowSettingsSaveItem[] | null
    try {
      normalizedRules = validateRules()
    } catch (error: any) {
      message.error(error.message || '规则校验失败')
      return
    }

    if (!normalizedRules) {
      return
    }

    setSaving(true)
    try {
      const response = await apiService.copyTrading.followSettingsSave({
        copyTradingId: selectedCopyTradingId,
        enabled,
        rules: normalizedRules,
      })
      if (response.data.code === 0 && response.data.data) {
        setEnabled(response.data.data.enabled)
        setRules((response.data.data.rules || []).map((rule) => createRuleRow(rule)))
        message.success('跟单设置已保存')
      } else {
        message.error(response.data.msg || '保存跟单设置失败')
      }
    } catch (error: any) {
      message.error(error.message || '保存跟单设置失败')
    } finally {
      setSaving(false)
    }
  }

  const selectedCopyTrading = copyTradings.find((item) => item.id === selectedCopyTradingId)

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap', marginBottom: 20 }}>
        <h2 style={{ margin: 0 }}>跟单设置</h2>
      </div>

      <Card loading={loading}>
        {copyTradings.length === 0 ? (
          <Empty description="暂无可设置的跟单配置" />
        ) : (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <div>
              <div style={{ fontSize: 13, color: '#666', marginBottom: 8 }}>选择跟单配置</div>
              <Select
                value={selectedCopyTradingId}
                style={{ width: '100%' }}
                onChange={(value) => {
                  setSelectedCopyTradingId(value)
                  setSearchParams({ copyTradingId: String(value) })
                }}
                options={copyTradings.map((item) => ({
                  value: item.id,
                  label: `${item.configName || `配置 ${item.id}`} / ${item.accountName || `账户 ${item.accountId}`} / ${item.leaderName || `Leader ${item.leaderId}`}`,
                }))}
              />
            </div>

            {selectedCopyTrading && (
              <Alert
                type="info"
                showIcon
                message={`当前正在设置：${selectedCopyTrading.configName || `配置 ${selectedCopyTrading.id}`}。规则命中后，会按这里的金额与单笔上限执行。`}
              />
            )}

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600 }}>启用跟单规则</div>
                <div style={{ fontSize: 12, color: '#666' }}>关闭后，这条配置不再按范围表判断买入金额</div>
              </div>
              <Switch checked={enabled} onChange={setEnabled} checkedChildren="开启" unCheckedChildren="关闭" />
            </div>

            <Divider style={{ margin: 0 }} />

            <div style={{ display: 'grid', gridTemplateColumns: '1.3fr 1.3fr 1fr 1fr 88px', gap: 12, fontSize: 12, color: '#666', fontWeight: 600 }}>
              <div>被跟单投注起点</div>
              <div>被跟单投注终点</div>
              <div>我方投入金额</div>
              <div>我方单笔上限</div>
              <div>操作</div>
            </div>

            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              {rules.map((rule) => (
                <div key={rule.rowKey} style={{ display: 'grid', gridTemplateColumns: '1.3fr 1.3fr 1fr 1fr 88px', gap: 12, alignItems: 'center' }}>
                  <InputNumber
                    stringMode
                    min="0"
                    style={{ width: '100%' }}
                    value={rule.minLeaderAmount || null}
                    onChange={(value) => updateRule(rule.rowKey, 'minLeaderAmount', value as string | null)}
                    placeholder="例如 0"
                    suffix="USDC"
                  />
                  <InputNumber
                    stringMode
                    min="0"
                    style={{ width: '100%' }}
                    value={rule.maxLeaderAmount || null}
                    onChange={(value) => updateRule(rule.rowKey, 'maxLeaderAmount', value as string | null)}
                    placeholder="留空表示不限"
                    suffix="USDC"
                  />
                  <InputNumber
                    stringMode
                    min="0"
                    style={{ width: '100%' }}
                    value={rule.followAmount || null}
                    onChange={(value) => updateRule(rule.rowKey, 'followAmount', value as string | null)}
                    placeholder="例如 10"
                    suffix="USDC"
                  />
                  <InputNumber
                    stringMode
                    min="0"
                    style={{ width: '100%' }}
                    value={rule.followMaxAmount || null}
                    onChange={(value) => updateRule(rule.rowKey, 'followMaxAmount', value as string | null)}
                    placeholder="例如 20"
                    suffix="USDC"
                  />
                  <Button danger icon={<DeleteOutlined />} onClick={() => removeRule(rule.rowKey)}>
                    删除
                  </Button>
                </div>
              ))}
            </Space>

            <Space>
              <Button icon={<PlusOutlined />} onClick={addRule}>
                增加范围
              </Button>
              <Button icon={<ClearOutlined />} onClick={clearRules}>
                清空
              </Button>
            </Space>

            <Alert
              type="warning"
              showIcon
              message="未命中任何范围时，这单会直接跳过，不跟单。结束金额留空表示这一档向上无限延伸。"
            />

            <div>
              <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
                保存
              </Button>
            </div>
          </Space>
        )}
      </Card>
    </div>
  )
}

export default FollowSettings
