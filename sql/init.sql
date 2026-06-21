SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS reservation;
DROP TABLE IF EXISTS slot;
DROP TABLE IF EXISTS stadium;

CREATE TABLE stadium
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '场地ID',
    name       VARCHAR(128)  NOT NULL COMMENT '场地名称',
    type       VARCHAR(32)   NOT NULL COMMENT '场地类型：BADMINTON(羽毛球) / BASKETBALL(篮球)',
    status     TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1-开放 0-关闭',
    deleted    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_type (type),
    INDEX idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='场地表';

CREATE TABLE slot
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '场次ID',
    stadium_id     BIGINT        NOT NULL COMMENT '关联场地ID',
    date           DATE          NOT NULL COMMENT '日期',
    start_time     TIME          NOT NULL COMMENT '开始时间',
    end_time       TIME          NOT NULL COMMENT '结束时间',
    total_stock    INT           NOT NULL DEFAULT 0 COMMENT '总库存',
    remaining_stock INT           NOT NULL DEFAULT 0 COMMENT '剩余库存',
    status         TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1-可预约 0-不可预约',
    deleted        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_stadium_id (stadium_id),
    INDEX idx_date (date),
    INDEX idx_stadium_date (stadium_id, date),
    CONSTRAINT fk_slot_stadium FOREIGN KEY (stadium_id) REFERENCES stadium (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='场次规格表';

CREATE TABLE reservation
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单ID',
    user_id     BIGINT        NOT NULL COMMENT '用户ID',
    slot_id     BIGINT        NOT NULL COMMENT '场次ID',
    status      VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT '订单状态：PENDING(待支付) / CONFIRMED(已预约) / CANCELLED(已取消)',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_slot_user (slot_id, user_id) COMMENT '防止同一用户重复预约同一场次',
    INDEX idx_user_id (user_id),
    INDEX idx_slot_id (slot_id),
    INDEX idx_status (status),
    CONSTRAINT fk_reservation_slot FOREIGN KEY (slot_id) REFERENCES slot (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='预约订单表';

INSERT INTO stadium (name, type, status) VALUES
    ('羽毛球馆A场', 'BADMINTON', 1),
    ('羽毛球馆B场', 'BADMINTON', 1),
    ('羽毛球馆C场', 'BADMINTON', 1),
    ('篮球馆A场', 'BASKETBALL', 1),
    ('篮球馆B场', 'BASKETBALL', 1),
    ('乒乓球馆A桌', 'TABLE_TENNIS', 1),
    ('乒乓球馆B桌', 'TABLE_TENNIS', 1),
    ('乒乓球馆C桌', 'TABLE_TENNIS', 1);

INSERT INTO slot (stadium_id, date, start_time, end_time, total_stock, remaining_stock)
SELECT s.id,
       d.slot_date,
       t.start_t,
       t.end_t,
       20 AS total_stock,
       20 AS remaining_stock
FROM stadium s
         CROSS JOIN (
    SELECT DATE_ADD(CURDATE(), INTERVAL n DAY) AS slot_date
    FROM (SELECT 0 AS n UNION SELECT 1 UNION SELECT 2) days
) d
         CROSS JOIN (
    SELECT '08:00' AS start_t, '09:00' AS end_t UNION ALL
    SELECT '09:00', '10:00' UNION ALL
    SELECT '10:00', '11:00' UNION ALL
    SELECT '11:00', '12:00' UNION ALL
    SELECT '14:00', '15:00' UNION ALL
    SELECT '15:00', '16:00' UNION ALL
    SELECT '16:00', '17:00' UNION ALL
    SELECT '17:00', '18:00' UNION ALL
    SELECT '18:00', '19:00' UNION ALL
    SELECT '19:00', '20:00' UNION ALL
    SELECT '20:00', '21:00'
) t
WHERE s.status = 1;

SET FOREIGN_KEY_CHECKS = 1;