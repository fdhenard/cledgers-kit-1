(ns fdhenard.cledgers.handlers
  (:require [cljs.pprint :as pp]
            [fdhenard.cledgers.db :as db]
            [re-frame.core :as rf]
            [cljs-uuid-utils.core :as uuid]
            [fdhenard.cledgers.utils :as utils]
            [ajax.core :as ajax]
            [malli.core :as malli]
            [fdhenard.cledgers.spec :as cledgers-spec]
            [cljs-time.core :as time]
            [cljs-time.coerce :as time-coerce]
            [vimsical.re-frame.cofx.inject :as inject]
            ))

(rf/reg-event-db
  :initialize-db
  (fn [_ _]
    (rf/dispatch [:fetch-user])
    (rf/dispatch [:fetch-transactions])
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

(rf/reg-event-fx
 :login
 (fn [{:keys [db] :as _cofx} [_ user]]
   #_(.log js/console "login event")
   {:db
    (cond-> (assoc db :is-fetching-user? false)
      user (assoc :user user))
    :fx [[:dispatch [:fetch-transactions]]]}))

(rf/reg-event-db
 :logout-frontend
 (fn [db _]
   #_(.log js/console "logging out ")
   (-> db
       (dissoc :user)
       (assoc :is-fetching-user? false))))

(rf/reg-event-fx
 :logout
 (fn [_cofx [_ _a]]
   (ajax/POST "/api/logout/")
   {:fx [[:dispatch [:logout-frontend]]]}))

(rf/reg-event-fx
 :fetch-user
 (fn [{:keys [db] :as _cofx} [_ _a]]
   (.log js/console "fetching user")
   (ajax/GET "/api/user"
             {:error-handler
              (fn [response]
                (when-not (= 401 (:status response))
                  (.log js/console "error" (utils/pp response)))
                (rf/dispatch [:logout-frontend]))
              :handler
              #(rf/dispatch [:login %])})
   {:db (assoc db :is-fetching-user? true)}))

(rf/reg-event-db
 :add-transaction
 (fn [db [_ {:keys [uuid] :as xaction_}]]
   (let [xaction$ (assoc xaction_ :time-created (time/now))]
    (if (malli/validate cledgers-spec/Transaction xaction$)
      (assoc-in db [:xactions uuid] xaction$)
      (let [explanation (malli/explain cledgers-spec/Transaction xaction$)]
        (pp/pprint {:transactions-invalid explanation})
        (throw (ex-info "transaction data invalid" {:explain explanation})))))))

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
 (fn [_cofx [_ filter-map]]
   ;; (pp/pprint {:fetch-transactions-var filter-map})
   (ajax/GET "/api/xactions/"
             {:params filter-map
              :error-handler #(.log js/console "error" (utils/pp %))
              :handler #(rf/dispatch [:set-transactions %])})
   {}))

(defn backend-xaction->frontend-xaction [{be-date :date :as back-xaction}]
  {:uuid (:xaction_uuid back-xaction)
   :date (time-coerce/to-local-date be-date)
   :description (:description back-xaction)
   :amount (:amount back-xaction)
   :payee {:name (:payee_name back-xaction)
           :id (:payee_id back-xaction)}
   :ledger {:name (:ledger_name back-xaction)
            :id (:ledger_id back-xaction)}
   :is-reconciled? (:is_reconciled back-xaction)
   :time-created (:time_created back-xaction)
   :backend-id (:xaction_id back-xaction)})

(rf/reg-event-db
 :set-transactions
 (fn [db [_ transactions-res]]
   #_(.log js/console "set-transactions" (utils/pp transactions-res))
   (let [xactions (->> (:result transactions-res)
                (map backend-xaction->frontend-xaction)
                (map (fn [xaction]
                       [(:uuid xaction) xaction]))
                (into {}))]
     (when-not (malli/validate
                [:map-of :string cledgers-spec/Transaction] xactions)
       (let [explanation (malli/explain
                          [:map-of :string cledgers-spec/Transaction]
                          xactions)]
        (pp/pprint {:transactions-invalid explanation})
        (throw
         (ex-info
          "transactions not valid"
          {:explain explanation}))))
     (assoc db :xactions xactions))))

(rf/reg-event-db
 :mark-reconciled
 (fn [db [_ xaction-uuids]]
   (let [new-xactions
         (reduce
          (fn [xactions-map xaction-uuid]
            (let [xaction (get xactions-map xaction-uuid)]
              (when (:is-reconciled? xaction)
                (throw (ex-info "shouldn't be reconciled" {:xaction xaction})))
              (let [new-xaction (assoc xaction :is-reconciled? true)]
                (assoc xactions-map xaction-uuid new-xaction))))
          (:xactions db)
          xaction-uuids)]
     (assoc db :xactions new-xactions))))


(rf/reg-event-fx
 :reconcile
 (rf/inject-cofx ::inject/sub [:total])
 (fn [{:keys [db total] :as _cofx} [_ _a]]
   (let [unreconciled-xaction-uuids
         (->> (:xactions db)
              (filter
               (fn [[_ xaction]]
                 (not (:is-reconciled? xaction))))
              (map first)
              set)]
     (ajax/POST "/api/reconcile"
                {:error-handler
                 (fn [err-data]
                   (.log js/console "error" (utils/pp err-data)))
                 :handler #(rf/dispatch [:mark-reconciled
                                         unreconciled-xaction-uuids])
                 :finally #(rf/dispatch [:done-reconciling])
                 :params {:amount total
                          :unreconciled-xaction-uuids
                          unreconciled-xaction-uuids}}))
   {:db (assoc db :is-reconciling? true)}))

(rf/reg-event-db
 :done-reconciling
 (fn [db [_ _]]
   (assoc db :is-reconciling? false)))

(rf/reg-event-db
 :edit
 (fn [db [_ edit-id]]
   (assoc db :editing-id edit-id)))

(rf/reg-event-fx
 :unreconcile
 (fn [{:keys [db] :as _cofx} [_ xaction-uuid]]
   (let [xaction (get-in db [:xactions xaction-uuid])]
     (ajax/PUT "/api/unreconcile"
               {:params {:xaction-uuid xaction-uuid
                         :xaction-amt (:amount xaction)}
                :handler
                #(rf/dispatch [:mark-unreconciled
                               xaction-uuid])
                :error-handler
                (fn [err-data]
                  (.log js/console "error" (utils/pp err-data))
                  (rf/dispatch [:mark-reconciled
                                [xaction-uuid]]))})
     {})))

(rf/reg-event-db
 :mark-unreconciled
 (fn [db [_ xaction-uuid]]
   (update-in
    db
    [:xactions xaction-uuid]
    #(assoc % :is-reconciled? false))))

(rf/reg-event-db
 :set-last-date-used
 (fn [db [_ new-last-date-used]]
   (assoc db :last-date-used new-last-date-used)))


(rf/reg-event-db
 :update-xaction
 (fn [db [_ updated-xaction]]
   (assoc-in db [:xactions (:uuid updated-xaction)] updated-xaction)))

(rf/reg-event-fx
 :fetch-ledger-totals
 (fn [_cofx [_ _]]
   (ajax/GET "/api/ledger-totals"
             {:error-handler
              #(.log
                js/console
                "error fetching ledger totals"
                (utils/pp %))
              :handler
              (fn [resp]
                (rf/dispatch [:set-ledger-totals resp]))})
   {}))

(rf/reg-event-db
 :set-ledger-totals
 (fn [db [_ ledger-tots-res]]
   #_(pp/pprint {:ledger-tots-res ledger-tots-res})
   (assoc db :ledger-totals (:result ledger-tots-res))))


(rf/reg-event-fx
 :set-ledger-filter
 (fn [cofx [_ ledger]]
   (let [ledger-id (:id ledger)]
    {:db (assoc (:db cofx) :ledger-filter-id ledger-id)
     :fx [[:dispatch [:fetch-transactions (when ledger-id
                                            {:ledger ledger-id})]]]})))
