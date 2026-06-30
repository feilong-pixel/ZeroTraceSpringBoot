SELECT /*%expand*/* FROM mail_info 
WHERE status = 0 OR (status = 9 AND retry_count < /* maxRetry */3)
