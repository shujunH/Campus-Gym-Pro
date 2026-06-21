# Campus-Gym-Pro 技术报告

## 一、项目概述

**项目名称**：Campus-Gym-Pro（高并发校园运动场预约系统）

**业务场景**：解决学校羽毛球馆、篮球馆、乒乓球馆在特定时间点（如每天早 8 点）抢位导致的瞬时高并发、系统崩溃、超卖（一人多占或多人抢同一位置）问题。

**技术栈**：

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.2.5 |
| 语言 | Java | 17 |
| ORM | MyBatis Plus | 3.5.6 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.x |
| 分布式锁 | Redisson | 3.27.2 |
| 消息队列 | RabbitMQ | 3.12 |
| 前端框架 | React | 18.2 |
| 构建工具 | Vite | 5.1 |
| 组件库 | Ant Design | 5.15 |
| 容器化 | Docker + Docker Compose | - |

---

## 二、系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        用户浏览器 (React)                         │
│                    http://localhost:3000                         │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP /api/*
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Spring Boot 后端 (8080)                        │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐  │
│  │ ReservationController│  │   SlotService   │  │   RedisConfig │  │
│  │  /api/reservation  │  │  cacheStockByDate │  │  Lua脚本加载  │  │
│  │  /api/slots        │  │  warmUpStock     │  │               │  │
│  │  /api/reservations │  │                  │  │               │  │
│  └────────┬───────────┘  └────────┬─────────┘  └───────┬───────┘  │
│           │                       │                     │          │
│  ┌────────┴───────────────────────┴─────────────────────┴───────┐  │
│  │                    ReservationService                         │  │
│  │  reserve() → 分布式锁 → 幂等检查 → Lua扣减 → MQ异步入库      │  │
│  │  processReservation() → 再次锁 → 检查 → MySQL扣减 → 入库    │  │
│  │  cancelReservation() → 状态变更 → 库存回滚                  │  │
│  └────────┬──────────────────┬──────────────────┬──────────────┘  │
│           │                  │                  │                  │
└───────────┼──────────────────┼──────────────────┼──────────────────┘
            │                  │                  │
            ▼                  ▼                  ▼
     ┌──────────┐      ┌──────────┐      ┌──────────┐
     │  MySQL   │      │  Redis   │      │ RabbitMQ │
     │  :3306   │      │  :6379   │      │  :5672   │
     └──────────┘      └──────────┘      └──────────┘
```

---

## 三、数据库设计

### 3.1 表结构

**三张核心表**：

- `stadium`（场地表）：存储场地名称、类型（BADMINTON/BASKETBALL/TABLE_TENNIS）、状态
- `slot`（场次表）：关联场地，定义日期+时间段，管理总库存和剩余库存
- `reservation`（订单表）：记录用户预约，状态流转 PENDING → CONFIRMED / CANCELLED

### 3.2 关键设计点

**防重复预约**：`reservation` 表设置了 `UNIQUE KEY uk_slot_user (slot_id, user_id)` 复合唯一索引，从数据库层面杜绝同一用户重复预约同一场次。

**逻辑删除**：所有表都使用 `deleted` 字段做逻辑删除（值为 0/1），配合 MyBatis Plus 的 `@TableLogic` 注解，查询时自动过滤已删除记录。

**索引设计**：`slot` 表对 `(stadium_id, date)` 建联合索引，加速按场地+日期查询场次。

---

## 四、核心亮点：高并发预约流程

这是整个项目最核心的部分，也是面试中最可能被问到的。一个预约请求经过以下完整链路：

### 4.1 预约流程图

```
用户点击「立即预约」
        │
        ▼
┌──────────────────────────┐
│ 1. Redisson 分布式锁      │  锁 key: lock:reservation:{userId}:{slotId}
│    锁有效期 5 秒           │  防止同一用户的重复请求并发执行
└──────────┬───────────────┘
           │ 获取锁成功
           ▼
┌──────────────────────────┐
│ 2. 幂等性检查（DB）        │  查询 MySQL 中该用户是否已有该时段订单
│    SELECT COUNT(*) FROM   │  有 → 返回"您已预约过该场次"
│    reservation WHERE ...   │
└──────────┬───────────────┘
           │ 无重复订单
           ▼
┌──────────────────────────┐
│ 3. Redis Lua 原子扣减库存  │  Lua 脚本在 Redis 服务端执行，单线程原子
│    GET slot:stock:{id}    │  → 返回 1:成功 / 0:库存不足 / -1:key不存在
│    if > 0 → DECR → 返回 1  │
└──────────┬───────────────┘
           │ 扣减成功
           ▼
┌──────────────────────────┐
│ 4. 发送 RabbitMQ 消息      │  将订单对象序列化发送到队列
│    或降级为同步处理        │  MQ不可用 → 直接调 processReservation()
└──────────┬───────────────┘
           │
           ▼
┌──────────────────────────┐
│ 5. Consumer 异步消费       │  @RabbitListener 监听队列
│    再次分布式锁 → 幂等检查  │  → MySQL乐观扣减 → INSERT 入库
└──────────────────────────┘
```

### 4.2 防超卖的三层保障

| 层次 | 机制 | 说明 |
|------|------|------|
| 第一层 | **Redis Lua 脚本原子扣减** | 在 Redis 单线程中执行 GET + DECR，保证原子性 |
| 第二层 | **Redisson 分布式锁 + DB 唯一索引** | 锁住 userId+slotId，防止同一用户并发重复请求 |
| 第三层 | **MySQL 乐观扣减** | `UPDATE slot SET remaining_stock = remaining_stock - 1 WHERE remaining_stock > 0`，数据库层面兜底 |

---

## 五、各技术组件详解

### 5.1 Redis + Lua 脚本

**为什么用 Lua？** Redis 是单线程执行命令的，但如果你在 Java 代码里先 GET 再 DECR，这两个操作之间可能被其他请求插入，导致超卖。Lua 脚本在 Redis 服务端**原子执行**，整个脚本作为一个整体，不会被其他命令打断。

**脚本逻辑**（`deduct_stock.lua`）：
```lua
local stockKey = KEYS[1]
local currentStock = redis.call('GET', stockKey)

if currentStock == false then
    return -1              -- Key 不存在，需要预热
end

currentStock = tonumber(currentStock)
if currentStock <= 0 then
    return 0               -- 库存不足
end

redis.call('DECR', stockKey)
return 1                   -- 扣减成功
```

### 5.2 Redisson 分布式锁

**为什么需要分布式锁？** 在高并发场景下，同一个用户可能同时发起多个请求（比如快速双击）。虽然 Redis Lua 扣减是原子的，但如果两个请求同时通过了 Redis 扣减，就会产生两条订单。分布式锁确保同一用户对同一场次的操作**串行执行**。

```java
RLock lock = redissonClient.getLock("lock:reservation:" + userId + ":" + slotId);
lock.tryLock(3, 5, TimeUnit.SECONDS);  // 等待3秒，锁5秒后自动释放
```

**锁的粒度**：锁的是 `userId + slotId`，不同用户之间互不影响，同一用户对不同场次也互不影响。

**看门狗机制**：Redisson 内部有 Watch Dog 机制，如果业务没执行完，锁会自动续期，不会因为 5 秒到期而误释放。

### 5.3 RabbitMQ 异步削峰

**为什么需要消息队列？** 早 8 点可能有 10000 人同时抢位。如果每个请求都同步写 MySQL，数据库连接池会被打满，导致系统崩溃。消息队列将"接受请求"和"处理请求"解耦：

- 请求进来 → Redis 扣减成功 → 发消息到队列 → 立即返回"排队中"（耗时 < 50ms）
- 后台 Consumer 慢慢消费消息 → 写 MySQL（可以控制并发消费数量）

**降级策略**：如果 RabbitMQ 不可用，直接降级为同步 MySQL 入库，保证系统可用性。

### 5.4 缓存预热

**问题**：系统启动后 Redis 中没有库存数据，用户第一次请求时才从 MySQL 加载（冷启动），体验差且可能出错。

**方案**：`SlotService` 中通过 `@Scheduled(cron = "0 0 0 * * ?")` 每天凌晨 0 点自动将次日场次库存加载到 Redis。同时 `reserve()` 中做了兜底：如果 Redis key 不存在，自动触发预热。

---

## 六、项目难点

### 难点 1：超卖问题的彻底解决

超卖是高并发秒杀系统的经典问题。本项目通过**三层防护**来解决：
- 第一层 Redis Lua 原子性 → 挡住 99% 的超卖
- 第二层分布式锁 + 幂等 → 挡住重复请求
- 第三层 MySQL 乐观扣减 → 数据库兜底

### 难点 2：分布式锁的粒度与性能平衡

锁太粗（如锁整个场次类型）会导致性能差，锁太细可能防不住。本项目选择 `userId + slotId` 粒度，既保证了正确性，又最大化了并发能力。

### 难点 3：分布式环境下数据一致性

Redis 扣减成功但 MySQL 入库失败怎么办？本项目通过 RabbitMQ 的 ACK 机制保证：Consumer 处理成功才 ACK 移除消息，处理失败则 NACK 重新入队。

### 难点 4：故障降级

RabbitMQ 不可用时，系统自动降级为同步 MySQL 入库，保证核心功能不中断。

---

## 七、面试可能被问到的问题

### Redis 相关
- Redis 是单线程的，为什么还快？（IO 多路复用）
- Lua 脚本为什么能保证原子性？
- 缓存穿透、缓存击穿、缓存雪崩是什么？怎么解决？
- Redis 和 Redisson 的关系？Redisson 实现了什么？

### 分布式锁相关
- Redisson 的看门狗机制是什么？
- 分布式锁需要注意什么问题？（死锁、锁误删、锁续期）
- Redis 分布式锁和 Zookeeper 分布式锁的区别？

### 消息队列相关
- 为什么要用消息队列？（解耦、削峰、异步）
- RabbitMQ 如何保证消息不丢失？（生产者确认、持久化、消费者 ACK）
- 消息重复消费怎么处理？（幂等性设计）

### 数据库相关
- MyBatis Plus 和 MyBatis 的区别？
- 唯一索引和普通索引的区别？
- 什么是逻辑删除？为什么要用？

### 系统设计相关
- 如果系统需要支持 10 万 QPS，你会怎么优化？
- 这个系统的瓶颈在哪里？如何扩展？
- 如何保证 Redis 和 MySQL 的数据一致性？

---

## 八、学习路线建议

按照以下顺序学习，可以在 2-3 周内达到能够自信讨论这个项目的水平：

### 第一阶段：基础知识（1 周）
1. **Java 基础**：集合、多线程、Lambda 表达式
2. **Spring Boot 基础**：IoC/DI、Controller、Service、Repository 分层
3. **MySQL 基础**：CRUD、索引、事务、隔离级别
4. **Redis 基础**：5 种数据类型、过期策略、持久化 RDB/AOF
5. **HTTP 协议**：GET/POST、状态码、RESTful API 设计

### 第二阶段：核心技术（1 周）
1. **MyBatis Plus**：BaseMapper、LambdaQueryWrapper、分页
2. **Redis 进阶**：Lua 脚本、缓存预热、缓存穿透/击穿/雪崩
3. **Redisson**：分布式锁原理、tryLock、看门狗
4. **RabbitMQ**：Exchange/Queue/Binding、消息确认 ACK、消费者并发
5. **React 基础**：JSX、组件、useState/useEffect、props、Ant Design

### 第三阶段：项目理解（3-4 天）
1. 通读 `ReservationService.java` 的 `reserve()` 方法，逐行理解
2. 理解 Lua 脚本的原子性原理
3. 理解三层防超卖的协作关系
4. 理解异步下单的消息流转
5. 在本地跑通项目，预约几次，观察 MySQL 和 Redis 数据变化

---

## 九、简历写法建议

```
项目名称：Campus-Gym-Pro 高并发校园运动场预约系统

技术栈：Spring Boot 3.x + MyBatis Plus + Redis + Redisson + RabbitMQ + React + MySQL

项目描述：
独立设计并开发了一个高并发校园运动场预约系统，解决高峰时段万人抢位的
超卖和系统崩溃问题。系统支持羽毛球、篮球、乒乓球三类场地预约。

核心工作：
- 使用 Redis + Lua 脚本实现库存原子扣减，杜绝超卖问题
- 基于 Redisson 分布式锁实现用户级别幂等性控制，防止重复提交
- 集成 RabbitMQ 消息队列进行异步削峰，将瞬时请求平滑处理
- 设计 MySQL 三层防护机制（唯一索引 + 乐观锁 + 分布式锁）
- 实现缓存预热机制，每日凌晨自动将次日库存加载至 Redis
- 使用 Docker Compose 实现一键部署（MySQL + Redis + RabbitMQ + 后端 + 前端）
- 前端使用 React + Ant Design 构建响应式管理界面
```

---

## 十、项目文件结构

```
Campus-Gym-Pro/
├── pom.xml                          # Maven依赖管理
├── docker-compose.yml               # 一键启动5个容器
├── Dockerfile                       # 后端镜像构建（多阶段）
├── .dockerignore
├── sql/
│   ├── schema.sql                   # 建表+种子数据
│   └── init.sql                     # Docker自动初始化
├── src/main/java/com/campusgym/pro/
│   ├── CampusGymProApplication.java # 启动入口
│   ├── config/
│   │   ├── RedisConfig.java         # Redis + Lua脚本配置
│   │   ├── RabbitMQConfig.java      # 交换机/队列/绑定
│   │   └── WebConfig.java           # CORS跨域
│   ├── entity/                      # 实体类（Stadium, Slot, Reservation）
│   ├── mapper/                      # MyBatis Plus Mapper接口
│   ├── dto/                         # 数据传输对象
│   ├── service/
│   │   ├── ReservationService.java  # ★ 核心：预约逻辑
│   │   ├── SlotService.java         # 缓存预热
│   │   └── ReservationConsumer.java # RabbitMQ消费者
│   └── controller/
│       └── ReservationController.java # REST API
├── src/main/resources/
│   ├── application.yml              # 全局配置
│   └── lua/
│       └── deduct_stock.lua         # ★ 库存扣减Lua脚本
└── frontend/                        # React前端
    ├── package.json
    ├── vite.config.js
    ├── index.html
    ├── Dockerfile
    ├── nginx.conf
    └── src/
        ├── main.jsx
        ├── App.jsx                  # 路由+布局
        ├── api/index.js             # Axios API封装
        └── pages/
            ├── HomePage.jsx         # 首页
            ├── ReservationPage.jsx  # 预约场地（Tabs分组）
            └── MyOrdersPage.jsx     # 我的预约（含场地详情）
```
