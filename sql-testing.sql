select * from xaction;

select amount from xaction
 where is_reconciled = false;
 
select sum(amount) from xaction
 where is_reconciled = false;

select 
	  coalesce(
		  (select decimal_value from properties
			where name = 'reconcile-amt')
	  , 0)
	  +
	  coalesce(
          (select sum(amount) from xaction
           where is_reconciled = false)
	  , 0)
	  as total;
	  
-- INSERT INTO customers (NAME, email)
-- VALUES('Microsoft','hotline@microsoft.com') 
-- ON CONFLICT ON CONSTRAINT customers_name_key 
-- DO NOTHING;

insert into properties 
  (name, decimal_value) values
  ('reconcile-amt', 2.34)
on conflict on constraint properties_name_key
  do update set decimal_value = EXCLUDED.decimal_value;

select * from properties;
delete from properties;

select * from xaction;

