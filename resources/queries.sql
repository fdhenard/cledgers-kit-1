-- place your sql queries here
-- see https://www.hugsql.org/ for documentation

-- :name create-user! :! :n
-- :doc creates a new user record
INSERT INTO cledgers_user
(username, first_name, last_name, email, pass, is_admin, is_active)
VALUES (:username, :first_name, :last_name, :email, :pass, :is_admin, :is_active)
