(ns fdhenard.cledgers.subscriptions
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub
  :page
  (fn [db _]
    (:page db)))

(reg-sub
  :docs
  (fn [db _]
    (:docs db)))


(reg-sub
 :time
 (fn [db _]
   (-> db :time)))

(reg-sub
 :time-color
 (fn [db _]
   (:time-color db)))

(reg-sub
 :xaction-editing-description
 (fn [db _]
   (get-in db [:xaction-editing :description])))

(reg-sub
 :xaction-editing-amount
 (fn [db _]
   (get-in db [:xaction-editing :amount])))

(reg-sub
 :xaction-editing-date
 (fn [db _]
   (get-in db [:xaction-editing :date])))

(reg-sub
 :xactions
 (fn [db _]
   ;; (.log js/console (str "db = " (pp db)))
   (get db :xactions)))

(reg-sub
 :user
 (fn [db _]
   (get db :user)))

;; (reg-sub
;;  :db
;;  (fn [db _]
;;    db))

(reg-sub
 :transactions
 (fn [db _]
   (:xactions db)))
