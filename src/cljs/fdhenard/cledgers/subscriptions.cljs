(ns fdhenard.cledgers.subscriptions
  (:require [cljs.pprint :as pp]
            [re-frame.core :as rf #_#_:refer [reg-sub]]
            [decimal.core :as decimal]))

(rf/reg-sub
  :page
  (fn [db _]
    (:page db)))

(rf/reg-sub
  :docs
  (fn [db _]
    (:docs db)))


(rf/reg-sub
 :time
 (fn [db _]
   (-> db :time)))

(rf/reg-sub
 :time-color
 (fn [db _]
   (:time-color db)))

(rf/reg-sub
 :xaction-editing-description
 (fn [db _]
   (get-in db [:xaction-editing :description])))

(rf/reg-sub
 :xaction-editing-amount
 (fn [db _]
   (get-in db [:xaction-editing :amount])))

(rf/reg-sub
 :xaction-editing-date
 (fn [db _]
   (get-in db [:xaction-editing :date])))

(rf/reg-sub
 :xactions
 (fn [db _]
   ;; (.log js/console (str "db = " (pp db)))
   (get db :xactions)))

(rf/reg-sub
 :user
 (fn [db _]
   (get db :user)))

(rf/reg-sub
 :is-fetching-user?
 (fn [db _]
   (true? (:is-fetching-user? db))))

;; (rf/reg-sub
;;  :db
;;  (fn [db _]
;;    db))

;; (rf/reg-sub
;;  :transactions
;;  (fn [db _]
;;    (:xactions db)))

(rf/reg-sub
 :total
 (fn [_query-v]
   (rf/subscribe [:xactions]))
 (fn [transactions _query-v]
   #_(pp/pprint {:transactions transactions})
   (when (seq transactions)
     (let [total-dec (->> transactions
                          (map (fn [[_ xaction]]
                                 (:amount xaction)))
                          (map decimal/decimal)
                          (reduce decimal/+))]
       (str total-dec)))))

(rf/reg-sub
 :is-reconciling?
 (fn [db _]
   (true? (:is-reconciling? db))))

(rf/reg-sub
 :xactions-sorted-by-date-desc
 (fn [_query-v]
   (rf/subscribe [:xactions]))
 (fn [xactions _something]
   #_(.log js/console "xactions sorted handler triggered")
   (->> xactions
        (map (fn [[_ xaction]] xaction))
        (sort-by (juxt :date :time-created) >))))

(rf/reg-sub
 :editing-id
 (fn [db _something]
   (:editing-id db)))

(rf/reg-sub
 :last-date-used
 (fn [db _something]
   (:last-date-used db)))

(rf/reg-sub
 :ledger-totals
 (fn [db _something]
   (:ledger-totals db)))

(rf/reg-sub
 :ledger-filter-id
 (fn [db _something]
   (:ledger-filter-id db)))
