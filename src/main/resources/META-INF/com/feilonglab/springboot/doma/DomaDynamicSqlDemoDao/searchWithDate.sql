SELECT *
FROM mail_info
WHERE 1 = 1

/*%if fromDate != null */
AND sent_at >= /* fromDate */'2026-01-01 00:00:00'
/*%end */
