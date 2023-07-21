

select x.ledger_id as ledger_id
     , l.name as ledger_name
	 , sum(x.amount) as total
  from xaction x
  join ledger l on l.id = x.ledger_id
 group by x.ledger_id, l.name