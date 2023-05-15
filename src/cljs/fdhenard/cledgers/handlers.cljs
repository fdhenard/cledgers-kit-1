(ns fdhenard.cledgers.handlers
  (:require [fdhenard.cledgers.db :as db]
            [re-frame.core :refer [dispatch reg-event-db reg-event-fx reg-fx]]
            [cljs-uuid-utils.core :as uuid]
            [fdhenard.cledgers.utils :as utils]
            #_[accountant.core :as accountant]
            [ajax.core :as ajax]
            ))

(reg-event-db
  :initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db
  :set-active-page
  (fn [db [_ page]]
    (.log js/console "setting active page to " page)
    (assoc db :page page)))

(reg-event-db
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

(reg-event-db
 :timer
 (fn [db [_ new-time]]
   (assoc db :time new-time)))

;; (reg-event-db
;;  :add-xaction
;;  (fn [db [_ new-xaction]]
;;    (let [new-id (str (uuid/make-random-uuid))]
;;      ;; (.log js/console "db" (utils/pp db))
;;      (.log js/console "stuffs:" (utils/pp {:new-id new-id
;;                                            :new-xaction new-xaction}))
;;      (let [reframe-res (-> db
;;                            (assoc-in [:xactions new-id] (merge {:id new-id} new-xaction)))]
;;        (ajax/POST "/api/xactions/"
;;                   {:params {:xaction new-xaction}
;;                    :error-handler (fn [err] (.log js/console "error: " (utils/pp err)))
;;                    :handler (fn [] (.log js/console "yay???"))})
;;        reframe-res))))

;; (reg-event-fx
;;  :navigate
;;  (fn [cofx [_ path]]
;;    (accountant/navigate! path)
;;    {}))

(reg-event-db
 :login
 (fn [db [_ user]]
   (assoc db :user user)))

(reg-event-db
 :logout
 (fn [db _]
   (dissoc db :user)))
