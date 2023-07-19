-- place your sql queries here
-- see https://www.hugsql.org/ for documentation

-- :name create-user! :! :n
-- :doc creates a new user record
INSERT INTO cledgers_user
(username, first_name, last_name, email, pass, is_admin, is_active)
VALUES (:username, :first_name, :last_name, :email, :pass, :is_admin, :is_active)

-- -- :name get-user :? :1
-- -- :doc retrieve a user given the id.
-- SELECT * FROM cledgers_user
-- WHERE id = :id

-- :name get-user-by-uname :? :1
SELECT * FROM cledgers_user
WHERE username = :username


-- :name get-payees :? :*
SELECT id, name
  FROM payee
 WHERE name LIKE :q
 LIMIT 10

-- :name get-ledgers :? :*
SELECT id, name
  FROM ledger
 WHERE name LIKE :q
 LIMIT 10

-- :name create-xaction! :! :n
-- :doc creates a new xaction
INSERT INTO xaction
  (description, amount, date, created_by_id, uuid, payee_id, ledger_id)
VALUES
  (:description, :amount, :date, :created-by-id, :uuid, :payee-id, :ledger-id)

-- :name create-payee! :returning-execute :1
-- :doc creates a new payee
INSERT INTO payee
  (name, created_by_id)
VALUES
  (:name, :created-by-id)
RETURNING id;

-- :name create-ledger! :returning-execute :1
INSERT INTO ledger
  (name, created_by_id)
VALUES
  (:name, :created-by-id)
RETURNING id;


-- :name get-transactions :? :*
select x.id as xaction_id, x.uuid as xaction_uuid, x.description,
       x.amount, x.date,  p.id as payee_id, p.name as payee_name,
       l.id as ledger_id, l.name as ledger_name, x.is_reconciled,
       x.time_created
  from xaction x
  join payee p on p.id = x.payee_id
  join ledger l on l.id = x.ledger_id

-- :name sum-xactions-for-reconcile :? :1
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


-- :name set-reconcile-amt! :! :n
insert into properties
  (name, decimal_value) values
  ('reconcile-amt', :reconcile-amt)
on conflict on constraint properties_name_key
  do update set decimal_value = EXCLUDED.decimal_value;

-- :name set-xactions-reconciled! :! :n
update xaction
  set is_reconciled = true
where is_reconciled = false;
