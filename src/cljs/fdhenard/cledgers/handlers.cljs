(ns fdhenard.cledgers.handlers
  (:require [fdhenard.cledgers.db :as db]
            [re-frame.core :as rf]
            [cljs-uuid-utils.core :as uuid]
            [fdhenard.cledgers.utils :as utils]
            [ajax.core :as ajax]
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

(rf/reg-event-db
 :login
 (fn [db [_ user]]
   (.log js/console "login event")
   (assoc db :user user)))

(rf/reg-event-db
 :logout
 (fn [db _]
   (dissoc db :user)))

(rf/reg-event-fx
 :fetch-user
 (fn [_evt [_ a]]
   (.log js/console "fetching user")
   (ajax/GET "/api/user"
             {#_#_:params {:username @username :password @password}
              :error-handler #(.log js/console "error" (utils/pp %))
              :handler #(do
                          ;; (.log js/console "res:" (utils/pp %))
                          (rf/dispatch [:login %]))})
   {}))
