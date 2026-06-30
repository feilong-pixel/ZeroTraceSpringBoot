-- 插入测试邮件数据（如果不存在则插入，避免重复运行导致数据堆积）
INSERT INTO mail_info (mail_id, status, retry_count, to_name, to_email, subject, content, version)
SELECT 1, 0, 0, 'Fei Long Lab', 'testuser@example.com', 'Spring Batch Test 1', '<p>This is mock email 1 processed by Spring Batch.</p>', 1
WHERE NOT EXISTS (SELECT 1 FROM mail_info WHERE mail_id = 1);

INSERT INTO mail_info (mail_id, status, retry_count, to_name, to_email, subject, content, version)
SELECT 2, 0, 0, 'Fei Long Lab', 'testuser@example.com', 'Spring Batch Test 2', '<p>This is mock email 2 processed by Spring Batch.</p>', 1
WHERE NOT EXISTS (SELECT 1 FROM mail_info WHERE mail_id = 2);

INSERT INTO mail_info (mail_id, status, retry_count, to_name, to_email, subject, content, version)
SELECT 3, 9, 1, 'Fei Long Lab', 'testuser@example.com', 'Spring Batch Test 3 (Retry)', '<p>This is mock email 3 (previously failed, retrying) processed by Spring Batch.</p>', 1
WHERE NOT EXISTS (SELECT 1 FROM mail_info WHERE mail_id = 3);
