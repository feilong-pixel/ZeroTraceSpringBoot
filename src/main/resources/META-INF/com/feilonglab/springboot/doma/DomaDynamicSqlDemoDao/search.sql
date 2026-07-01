SELECT *
FROM mail_info
WHERE 1 = 1

/*%if status != null */
AND status = /* status */0
/*%end */

/*%if keyword != null */
AND subject LIKE /* @infix(keyword) */'abc'
/*%end */
