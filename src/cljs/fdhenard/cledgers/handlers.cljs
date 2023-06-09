(ns fdhenard.cledgers.handlers
  (:require [fdhenard.cledgers.db :as db]
            [re-frame.core :as rf]
            [cljs-uuid-utils.core :as uuid]
            [fdhenard.cledgers.utils :as utils]
            [ajax.core :as ajax]
            [malli.core :as malli]
            [fdhenard.cledgers.spec :as cledgers-spec]
            [cljs-time.core :as time]
            [cljs-time.coerce :as time-coerce]
            ))

(rf/reg-event-db
  :initialize-db
  (fn [_ _]
    db/default-db))

(rf/reg-event-db
  :set-active-page
  (fn [db [_ page]]
    (.log js/console "setting active page to " page)
    (assoc db :page page)))

(rf/reg-event-db
  :set-docs
  (fn [db [_ docs]]
    (assoc db :docs docs)))


(defn make-empty-xaction []
  {:date ""
   :description ""
   :amount ""})

;; (reg-event-db
;;  :initialize
;;  (fn [_ _]
;;    (.log js/console "here!")
;;    {:time (js/Date.)
;;     :time-color "#f88"
;;     :xaction-editing (make-empty-xaction)
;;     :xactions {}
;;     :user nil}))

(rf/reg-event-db
 :timer
 (fn [db [_ new-time]]
   (assoc db :time new-time)))

(rf/reg-event-db
 :login
 (fn [db [_ user]]
   #_(.log js/console "login event")
   (when user
     (-> db
         (assoc :user user)
         (assoc :is-fetching-user? false)))))

(rf/reg-event-db
 :logout
 (fn [db _]
   #_(.log js/console "logging out ")
   (-> db
       (dissoc :user)
       (assoc :is-fetching-user? false))))

(rf/reg-event-fx
 :fetch-user
 (fn [{:keys [db]} [_ _a]]
   (.log js/console "fetching user")
   (ajax/GET "/api/user"
             {:error-handler #_(.log js/console "error" (utils/pp %))
              (fn [response]
                (when-not (= 401 (:status response))
                  (.log js/console "error" (utils/pp response)))
                (rf/dispatch [:logout]))
              :handler #(do
                          ;; (.log js/console "res:" (utils/pp %))
                          (rf/dispatch [:login %]))})
   {:db (assoc db :is-fetching-user? true)}))

(rf/reg-event-db
 :add-transaction
 (fn [db [_ {:keys [uuid] :as xaction}]]
   (malli/validate cledgers-spec/Transaction xaction)
   (assoc-in db [:xactions uuid] xaction)))

(rf/reg-event-db
 :remove-transaction
 (fn [db [_ xaction-uuid]]
   (update-in db [:xactions] dissoc xaction-uuid)))

(rf/reg-event-db
 :transaction-fully-added
 (fn [db [_ xaction-uuid]]
   (update-in db [:xactions xaction-uuid] dissoc :add-waiting?)))

(rf/reg-event-fx
 :fetch-transactions
 (fn [_evt [_ _a]]
   (ajax/GET "/api/xactions/"
             {:error-handler #(.log js/console "error" (utils/pp %))
              :handler #(rf/dispatch [:set-transactions %])})
   {}))

(defn backend-xaction->frontend-xaction [{be-date :date :as back-xaction}]
  (let [date (time-coerce/to-date-time be-date)]
   {:uuid (:xaction_uuid back-xaction)
    :date {:month (time/month date)
           :day (time/day date)
           :year (time/year date)}
    :description (:description back-xaction)
    :amount (:amount back-xaction)
    :payee {:name (:payee_name back-xaction)
            :id (:payee_id back-xaction)}
    :ledger {:name (:ledger_name back-xaction)
             :id (:ledger_id back-xaction)}}))

(rf/reg-event-db
 :set-transactions
 (fn [db [_ transactions-res]]
   #_(.log js/console "set-transactions" (utils/pp transactions-res))
   (let [#_#_$ (transit/read (transit/reader :json) transactions-res)
         $ (->> (:result transactions-res)
                (map backend-xaction->frontend-xaction)
                (map (fn [xaction]
                       [(:uuid xaction) xaction]))
                (into {}))]
     (malli/validate [:map-of :string cledgers-spec/Transaction]
                   $)
     (assoc db :xactions $))))
