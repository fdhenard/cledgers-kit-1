(ns fdhenard.cledgers.dev.scripts
  (:require
   [clojure.pprint :as pp]
   [buddy.hashers :as hashers]
   [integrant.repl.state :as state]))

(defn create-user-s! [& {:keys [username first-name last-name email
                                pass is-admin? is-active?],
                         :as u-map}]
  (pp/pprint {:u-map u-map})
  (let [db-res
        ((:db.sql/query-fn state/system)
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

  (create-user-s!
   :username "frank"
   :last-name "Henard"
   :first-name "Frank"
   :email "fdhenard@gmail.com"
   :pass "tanky"
   :is-admin? true
   :is-active? true)

  )
