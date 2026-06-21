import React, { useEffect, useState } from 'react'
import { Table, Tag, Typography, Button, Select, Space, Popconfirm, message, Spin, Alert, Empty } from 'antd'
import { getReservations, getReservationUsers, cancelReservation } from '../api'
import dayjs from 'dayjs'

const { Title, Text } = Typography

const statusMap = {
  PENDING: { text: '待支付', color: 'gold' },
  CONFIRMED: { text: '已预约', color: 'green' },
  CANCELLED: { text: '已取消', color: 'default' },
}

const typeMap = {
  BADMINTON: '🏸 羽毛球',
  BASKETBALL: '🏀 篮球',
  TABLE_TENNIS: '🏓 乒乓球',
}

export default function MyOrdersPage() {
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(false)
  const [userIds, setUserIds] = useState([])
  const [userId, setUserId] = useState(null)
  const [cancelling, setCancelling] = useState({})

  useEffect(() => {
    getReservationUsers()
      .then(res => {
        setUserIds(res.data)
        if (res.data.length > 0 && !userId) {
          setUserId(res.data[0])
        }
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!userId) return
    const fetchOrders = async () => {
      setLoading(true)
      try {
        const res = await getReservations(userId)
        setOrders(res.data)
      } catch (_err) {
        message.error('加载订单失败')
      } finally {
        setLoading(false)
      }
    }
    fetchOrders()
  }, [userId])

  const fetchOrders = async () => {
    if (!userId) return
    setLoading(true)
    try {
      const res = await getReservations(userId)
      setOrders(res.data)
    } catch (_err) {
      message.error('加载订单失败')
    } finally {
      setLoading(false)
    }
  }

  const handleCancel = async (record) => {
    setCancelling(prev => ({ ...prev, [record.id]: true }))
    try {
      const res = await cancelReservation(record.id, userId)
      if (res.data.success) {
        message.success('取消成功')
        fetchOrders()
      } else {
        message.warning(res.data.message)
      }
    } catch (_err) {
      message.error('取消失败')
    } finally {
      setCancelling(prev => ({ ...prev, [record.id]: false }))
    }
  }

  const columns = [
    {
      title: '订单ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
    },
    {
      title: '场地名称',
      dataIndex: 'stadiumName',
      key: 'stadiumName',
      width: 160,
      render: (val, record) => (
        <Space>
          <Tag>{typeMap[record.stadiumType] || record.stadiumType}</Tag>
          <Text strong>{val || '-'}</Text>
        </Space>
      ),
    },
    {
      title: '预约日期',
      dataIndex: 'date',
      key: 'date',
      width: 110,
      render: (val) => val || '-',
    },
    {
      title: '时间段',
      key: 'timeRange',
      width: 130,
      render: (_, record) => record.startTime && record.endTime
        ? `${record.startTime} - ${record.endTime}`
        : '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (status) => {
        const cfg = statusMap[status] || { text: status, color: 'default' }
        return <Tag color={cfg.color}>{cfg.text}</Tag>
      },
    },
    {
      title: '下单时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (val) => val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => {
        if (record.status === 'CANCELLED') return <Text type="secondary">-</Text>
        return (
          <Popconfirm
            title="确认取消该预约？"
            onConfirm={() => handleCancel(record)}
            okText="确认"
            cancelText="返回"
          >
            <Button
              danger
              size="small"
              loading={cancelling[record.id]}
            >
              取消
            </Button>
          </Popconfirm>
        )
      },
    },
  ]

  return (
    <div>
      <Title level={2}>我的预约</Title>

      <Alert
        message="状态说明"
        description="PENDING(排队中)：请求已通过预检，等待处理；CONFIRMED(已预约)：订单已确认；CANCELLED(已取消)：用户主动取消。"
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      <Space style={{ marginBottom: 16 }}>
        <Text strong>选择用户：</Text>
        <Select
          placeholder="请选择已预约的用户"
          style={{ width: 180 }}
          value={userId}
          onChange={v => setUserId(v)}
          options={userIds.map(id => ({ value: id, label: `用户 ${id}` }))}
          showSearch
          filterOption={(input, option) =>
            String(option.value).includes(input)
          }
        />
        <Button onClick={fetchOrders}>刷新</Button>
      </Space>

      {!userId ? (
        <Empty description="暂无已预约用户，请先在预约页面完成一次预约" />
      ) : loading ? (
        <Spin size="large" style={{ display: 'block', margin: '60px auto' }} />
      ) : orders.length === 0 ? (
        <Empty description={`用户 ${userId} 暂无预约记录`} />
      ) : (
        <Table
          columns={columns}
          dataSource={orders}
          rowKey="id"
          pagination={{ pageSize: 10 }}
          bordered
        />
      )}
    </div>
  )
}