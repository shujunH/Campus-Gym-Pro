import React, { useEffect, useState, useCallback } from 'react'
import { Card, Row, Col, Button, DatePicker, Tabs, Tag, Typography, Space, message, Spin, Badge, Alert, InputNumber } from 'antd'
import { CheckCircleOutlined, ClockCircleOutlined, ThunderboltOutlined } from '@ant-design/icons'
import { getStadiums, getSlots, reserve } from '../api'
import dayjs from 'dayjs'

const { Title, Text } = Typography

const typeMeta = {
  BADMINTON: { label: '🏸 羽毛球', color: 'green', key: 'BADMINTON' },
  BASKETBALL: { label: '🏀 篮球', color: 'orange', key: 'BASKETBALL' },
  TABLE_TENNIS: { label: '🏓 乒乓球', color: 'blue', key: 'TABLE_TENNIS' },
}

export default function ReservationPage() {
  const [stadiums, setStadiums] = useState([])
  const [slots, setSlots] = useState([])
  const [typeSlots, setTypeSlots] = useState({})
  const [loading, setLoading] = useState(false)
  const [date, setDate] = useState(dayjs())
  const [activeType, setActiveType] = useState('BADMINTON')
  const [reserving, setReserving] = useState({})
  const [userId, setUserId] = useState(1001)
  const [userIdInput, setUserIdInput] = useState(1001)

  const fetchAllSlots = useCallback(async () => {
    setLoading(true)
    try {
      const types = ['BADMINTON', 'BASKETBALL', 'TABLE_TENNIS']
      const results = await Promise.all(
        types.map(t => getSlots(date.format('YYYY-MM-DD'), t))
      )
      const grouped = {}
      const all = []
      types.forEach((t, i) => {
        grouped[t] = results[i].data
        all.push(...results[i].data)
      })
      setTypeSlots(grouped)
      setSlots(all)
    } catch (_err) {
      message.error('加载场次失败，请确保后端服务已启动')
    } finally {
      setLoading(false)
    }
  }, [date])

  useEffect(() => {
    getStadiums().then(res => setStadiums(res.data)).catch(() => {})
    fetchAllSlots()
  }, [fetchAllSlots])

  const stadiumMap = {}
  stadiums.forEach(s => { stadiumMap[s.id] = s })

  const filteredSlots = typeSlots[activeType] || []

  const handleReserve = async (slot) => {
    const key = `reserve-${slot.id}`
    setReserving(prev => ({ ...prev, [key]: true }))
    try {
      const res = await reserve(userId, slot.id)
      if (res.data.success) {
        message.success(res.data.message)
        fetchAllSlots()
      } else {
        message.warning(res.data.message)
      }
    } catch (_err) {
      message.error('预约请求失败，请稍后重试')
    } finally {
      setReserving(prev => ({ ...prev, [key]: false }))
    }
  }

  const disabledDate = (current) => {
    return current && current < dayjs().startOf('day')
  }

  const tabItems = Object.values(typeMeta).map(meta => ({
    key: meta.key,
    label: (
      <span>
        {meta.label}
        <Tag color={meta.color} style={{ marginLeft: 8 }}>
          {typeSlots[meta.key]?.filter(s => s.remainingStock > 0).length || 0} 场可约
        </Tag>
      </span>
    ),
  }))

  return (
    <div>
      <Title level={2}>
        <ThunderboltOutlined /> 预约场地
      </Title>

      <Alert
        message="高并发提示"
        description="系统采用 Redis + Lua 原子扣减 + Redisson 分布式锁 + RabbitMQ 异步削峰，支持万人同时抢位。"
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      <Space style={{ marginBottom: 16 }} size="middle" wrap>
        <span>
          <Text strong>模拟用户ID：</Text>
          <InputNumber
            min={1}
            max={99999}
            value={userIdInput}
            onChange={v => setUserIdInput(v)}
            style={{ width: 120, marginLeft: 8 }}
          />
          <Button type="link" onClick={() => setUserId(userIdInput)}>切换</Button>
          <Tag color="blue">当前用户: {userId}</Tag>
        </span>
        <DatePicker value={date} onChange={setDate} disabledDate={disabledDate} allowClear={false} />
        <Button type="primary" onClick={fetchAllSlots} loading={loading}>
          刷新场次
        </Button>
      </Space>

      <Tabs
        activeKey={activeType}
        onChange={setActiveType}
        items={tabItems}
        style={{ marginBottom: 8 }}
      />

      {loading ? (
        <Spin size="large" style={{ display: 'block', margin: '60px auto' }} />
      ) : (
        <Row gutter={[16, 16]}>
          {filteredSlots.map(slot => {
            const stadium = stadiumMap[slot.stadiumId]
            const meta = typeMeta[activeType] || {}
            const isFull = slot.remainingStock <= 0
            const reserveKey = `reserve-${slot.id}`
            return (
              <Col xs={24} sm={12} md={8} lg={6} key={slot.id}>
                <Badge.Ribbon
                  text={isFull ? '已约满' : `剩余 ${slot.remainingStock}`}
                  color={isFull ? 'red' : 'green'}
                >
                  <Card
                    hoverable={!isFull}
                    size="small"
                    title={
                      <Space>
                        <Tag color={meta.color}>{stadium?.name || `场地#${slot.stadiumId}`}</Tag>
                      </Space>
                    }
                    style={{ opacity: isFull ? 0.6 : 1 }}
                  >
                    <div style={{ marginBottom: 12 }}>
                      <Text type="secondary">
                        <ClockCircleOutlined /> {slot.startTime} - {slot.endTime}
                      </Text>
                    </div>
                    <div style={{ marginBottom: 12 }}>
                      <Text>库存：</Text>
                      <Text strong style={{ color: isFull ? '#ff4d4f' : '#52c41a' }}>
                        {slot.remainingStock}/{slot.totalStock}
                      </Text>
                    </div>
                    <Button
                      type="primary"
                      block
                      icon={<CheckCircleOutlined />}
                      disabled={isFull}
                      loading={reserving[reserveKey]}
                      onClick={() => handleReserve(slot)}
                    >
                      {isFull ? '已约满' : '立即预约'}
                    </Button>
                  </Card>
                </Badge.Ribbon>
              </Col>
            )
          })}
          {filteredSlots.length === 0 && (
            <Col span={24}>
              <Card>
                <Text type="secondary">该日期暂无可用场次，请选择其他日期</Text>
              </Card>
            </Col>
          )}
        </Row>
      )}
    </div>
  )
}