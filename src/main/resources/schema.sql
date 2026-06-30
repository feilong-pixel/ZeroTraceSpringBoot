-- 邮件信息表
CREATE TABLE IF NOT EXISTS mail_info (
    mail_id INTEGER PRIMARY KEY AUTOINCREMENT,
    status INTEGER DEFAULT 0,
    retry_count INTEGER DEFAULT 0,
    sent_at DATETIME,
    to_name VARCHAR(100) NOT NULL,
    to_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    version INTEGER DEFAULT 1
);

-- 邮件发送日志表
CREATE TABLE IF NOT EXISTS mail_send_log (
    log_id INTEGER PRIMARY KEY AUTOINCREMENT,
    mail_id INTEGER NOT NULL,
    sent_at DATETIME NOT NULL,
    status INTEGER NOT NULL,
    attempt INTEGER NOT NULL,
    error_reason TEXT,
    version INTEGER DEFAULT 1,
    FOREIGN KEY(mail_id) REFERENCES mail_info(mail_id)
);

-- 创建索引以加速未发送和失败重试邮件的轮询
CREATE INDEX IF NOT EXISTS idx_mail_info_status ON mail_info(status, retry_count);

-- 修复历史数据中因直接存储毫秒级时间戳导致在可视化工具中显示为 9999/12/31 12:00:00 的问题
UPDATE mail_info 
SET sent_at = datetime(sent_at / 1000, 'unixepoch') 
WHERE sent_at IS NOT NULL AND typeof(sent_at) = 'integer';

UPDATE mail_send_log 
SET sent_at = datetime(sent_at / 1000, 'unixepoch') 
WHERE sent_at IS NOT NULL AND typeof(sent_at) = 'integer';
