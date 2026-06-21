import React, { useState } from 'react'
import { BrowserRouter, Routes, Route, NavLink } from 'react-router-dom'
import { Layout, Menu, ConfigProvider, theme } from 'antd'
import { HomeOutlined, CalendarOutlined, OrderedListOutlined } from '@ant-design/icons'
import zhCN from 'antd/locale/zh_CN'
import HomePage from './pages/HomePage'
import ReservationPage from './pages/ReservationPage'
import MyOrdersPage from './pages/MyOrdersPage'

const { Header, Content, Footer } = Layout

export default function App() {
  const [current, setCurrent] = useState('home')

  const items = [
    { key: 'home', icon: <HomeOutlined />, label: <NavLink to="/">首页</NavLink> },
    { key: 'reserve', icon: <CalendarOutlined />, label: <NavLink to="/reserve">预约场地</NavLink> },
    { key: 'orders', icon: <OrderedListOutlined />, label: <NavLink to="/orders">我的预约</NavLink> },
  ]

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 6,
        },
      }}
    >
      <BrowserRouter>
        <Layout style={{ minHeight: '100vh' }}>
          <Header style={{ display: 'flex', alignItems: 'center', padding: '0 50px' }}>
            <div style={{ color: '#fff', fontSize: 20, fontWeight: 'bold', marginRight: 40, whiteSpace: 'nowrap' }}>
              🏟 Campus Gym Pro
            </div>
            <Menu
              theme="dark"
              mode="horizontal"
              selectedKeys={[current]}
              onClick={({ key }) => setCurrent(key)}
              items={items}
              style={{ flex: 1 }}
            />
          </Header>
          <Content style={{ padding: '24px 50px' }}>
            <div style={{ background: '#fff', borderRadius: 8, padding: 24, minHeight: 400 }}>
              <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/reserve" element={<ReservationPage />} />
                <Route path="/orders" element={<MyOrdersPage />} />
              </Routes>
            </div>
          </Content>
          <Footer style={{ textAlign: 'center' }}>
            Campus Gym Pro - 高并发校园运动场预约系统 ©2026
          </Footer>
        </Layout>
      </BrowserRouter>
    </ConfigProvider>
  )
}