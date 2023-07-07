(ns fdhenard.cledgers.dev.scripts
  (:require
   [clojure.pprint :as pp]
   [buddy.hashers :as hashers]))

(defn create-user-s! [{query-fn :db.sql/query-fn :as _opts}
                      {:keys [username first-name last-name email
                              pass is-admin? is-active?],
                       :as u-map}]
  (pp/pprint {:u-map u-map})
  (let [db-res
        (query-fn
         :create-user!
         {:username username
          :first_name first-name
          :last_name last-name
          :email email
          :is_admin is-admin?
          :is_active is-active?
          :pass (hashers/derive pass)})]
    {:num-added db-res}))

(comment

  (require '[integrant.repl.state :as state])
  state/system

  (create-user-s!
   state/system
   {:username "frank"
    :last-name "Henard"
    :first-name "Frank"
    :email "fdhenard@gmail.com"
    :pass "tanky"
    :is-admin? true
    :is-active? true})

  (hashers/derive "")

  )
