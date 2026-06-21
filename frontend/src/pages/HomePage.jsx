import React, { useEffect, useState } from 'react'
import { Card, Row, Col, Statistic, Typography, Spin, Alert, Tag, Space } from 'antd'
import { TrophyOutlined, FieldTimeOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { getStadiums, getSlots } from '../api'
import dayjs from 'dayjs'

const { Title, Paragraph } = Typography

export default function HomePage() {
  const [stadiums, setStadiums] = useState([])
  const [todaySlots, setTodaySlots] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [sRes, tRes] = await Promise.all([
          getStadiums(),
          getSlots(dayjs().format('YYYY-MM-DD')),
        ])
        setStadiums(sRes.data)
        setTodaySlots(tRes.data)
      } catch (_err) {
        setError('无法连接到服务器，请确保后端已启动')
      } finally {
        setLoading(false)
      }
    }
    fetchData()
  }, [])

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />
  if (error) return <Alert message={error} type="error" showIcon />

  const badmintonCount = stadiums.filter(s => s.type === 'BADMINTON').length
  const basketballCount = stadiums.filter(s => s.type === 'BASKETBALL').length
  const availableSlots = todaySlots.filter(s => s.remainingStock > 0).length

  return (
    <div>
      <Title level={2}>欢迎使用 Campus Gym Pro</Title>
      <Paragraph type="secondary">
        高并发校园运动场预约系统 - 支持万人同时抢位，保障公平公正
      </Paragraph>

      <Row gutter={16} style={{ marginTop: 24 }}>
        <Col span={8}>
          <Card>
            <Statistic title="羽毛球场地" value={badmintonCount} prefix={<TrophyOutlined />} suffix="个" />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="篮球场地" value={basketballCount} prefix={<TrophyOutlined />} suffix="个" />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="今日可预约场次" value={availableSlots} prefix={<CheckCircleOutlined />} suffix="场" />
          </Card>
        </Col>
      </Row>

      <Title level={3} style={{ marginTop: 32 }}>技术架构</Title>
      <Row gutter={[16, 16]}>
        {[
          { title: 'Redis + Lua 原子扣减', desc: '使用 Redis 缓存库存，Lua 脚本保证原子性操作，杜绝超卖', color: 'red' },
          { title: 'Redisson 分布式锁', desc: '基于用户+场次加锁，5秒有效期防止暴力点击重复请求', color: 'orange' },
          { title: 'RabbitMQ 异步削峰', desc: '下单请求通过消息队列异步处理，平滑高峰期流量冲击', color: 'purple' },
          { title: '数据库兜底', desc: 'MySQL 唯一索引 (slot_id + user_id) 防止同一人重复预约', color: 'blue' },
          { title: '缓存预热', desc: '每日凌晨自动将次日库存加载进 Redis，保证高可用', color: 'green' },
          { title: '幂等性设计', desc: '多层幂等检查：分布式锁 + DB唯一索引 + 业务层校验', color: 'cyan' },
        ].map((item) => (
          <Col span={8} key={item.title}>
            <Card size="small" title={<Space><FieldTimeOutlined /><span>{item.title}</span></Space>}>
              <Tag color={item.color}>{item.color}</Tag>
              <Paragraph style={{ marginTop: 8 }}>{item.desc}</Paragraph>
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  )
}