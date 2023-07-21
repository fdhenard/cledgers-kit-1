(ns fdhenard.cledgers.core
  (:require [clojure.string :as string]
            [cljs.pprint :as pp]
            [reagent.core :as r]
            [reagent.dom :as d]
            [re-frame.core :as rf]
            ;; [goog.events :as events]
            ;; [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [fdhenard.cledgers.handlers]
            [fdhenard.cledgers.subscriptions]
            [fdhenard.cledgers.pages.login :as login-page]
            [fdhenard.cledgers.utils :as utils]
            [ajax.core :as ajax]
            [cljs-uuid-utils.core :as uuid]
            [cljs-time.core :as time]
            [cljs-time.format :as time-fmt]
            [fdhenard.cledgers.bulma-typeahead :as typeahead]
            [reitit.frontend.easy :as rfe]
            [reitit.frontend.controllers :as rfc]
            [reitit.frontend]
            [reitit.coercion.schema :as rsc]
            [re-frisk-remote.core :as re-frisk-remote]
            [fdhenard.cledgers.pages.about :as about-page]
            [fdhenard.cledgers.pages.ledger-balances :as ledger-balances-page])
  #_(:import goog.History))

;; -------------------------
;; Views

(defn navbar []
  (let [burger-expanded (r/atom false)]
   (fn []
     [:nav.navbar {:role "navigation" :aria-label "maim navigation"}
      [:div.navbar-brand
       [:div.navbar-item "fdhenard.cledgers"]
       [:div.navbar-burger
        {:role "button"
         :aria-label "menu"
         :aria-expanded false
         :data-target "cledgers-navbar-menu"
         :class (if-not @burger-expanded
                  #{}
                  #{"is-active"})
         :on-click (fn [_evt]
                     (swap! burger-expanded #(not %)))}
        [:span {:aria-hidden true}]
        [:span {:aria-hidden true}]
        [:span {:aria-hidden true}]]]
      [:div.navbar-menu
       {:id "cledgers-navbar-menu"
        :class (if-not @burger-expanded
                 #{}
                 #{"is-active"})}
       [:div.navbar-start]
       [:div.navbar-end
        [:div.navbar-item.has-dropdown.is-hoverable
         [:a.navbar-link "User"]
         [:div.navbar-dropdown
          [:a.navbar-item
           {:href "#"
            :on-click
            (fn [_evt]
              (rf/dispatch [:logout]))}
           "logout"]]]]]])))


;; (defn about-page []
;;   [:div.container
;;    [:div.row
;;     [:div.col-md-12
;;      #_[:img {:src (str js/context "/img/warning_clojure.png")}]
;;      [:img {:src "/img/warning_clojure.png"}]
;;      #_"warning! clojure"]]])




(defn dispatch-timer-event []
  (let [now (js/Date.)]
    (rf/dispatch [:timer now])))

(defn clock []
  [:div.example-clock
   {:style {:color @(rf/subscribe [:time-color])}}
   (let [time @(rf/subscribe [:time])]
     (if (not time)
       "oops"
       (-> time
           .toTimeString
           (string/split " ")
           first)))])

(defonce do-timer (js/setInterval dispatch-timer-event 1000))

;; (def last-date-used (atom (time/today)))

(comment

  (def local-dt-fmt (time-fmt/formatter "yyyy-MM-dd"))
  local-dt-fmt

  (def today (time/today))
  (def yesterday (time/local-date 2023 7 18))

  (> today yesterday)
  (< today yesterday)


  )

(defn empty-xaction []
  {:uuid (str (uuid/make-random-uuid))
   :date @(rf/subscribe [:last-date-used])
   :description ""
   :amount ""
   :add-waiting? true
   :is-reconciled? false})

(defn xform-xaction-for-backend [xaction]
  (-> xaction
      (dissoc :add-waiting?)
      (assoc :date
             (time-fmt/unparse-local-date
              {:format-str "yyyy-MM-dd"}
              (:date xaction)))))

(defn get-payees! [q-str callback]
  #_(println "callback:" callback)
  (let [response->results
        (fn [response]
          (let [payees (-> response :result)]
            (callback payees)))]

   (ajax/GET "/api/payees"
             {:params {:q q-str}
              :handler response->results
              :error-handler (fn [err]
                               (.log js/console "error: " (utils/pp err)))})))


(defn get-ledgers! [q-str callback]
  (let [response->results
        (fn [response]
          (let [ledgers (-> response :result)]
            (callback ledgers)))]
    (ajax/GET "/api/ledgers"
              {:params {:q q-str}
               :handler response->results
               :error-handler (fn [err]
                                (.log js/console "error: " (utils/pp err)))})))

(defn editable-xaction-row [xaction-in]
  (let [xaction-for-edit (r/atom xaction-in)
        payee-ta-atom (r/atom
                       {:textbox-val
                        (get-in xaction-in [:payee :name])
                        :matches #{}
                        :selection-val
                        (get-in xaction-in [:payee :name])})
        ledger-ta-atom (r/atom
                        {:textbox-val
                         (get-in xaction-in [:ledger :name])
                         :matches #{}
                         :selection-val
                         (get-in xaction-in [:ledger :name])})]
    (fn []
      [:tr {:key (:uuid @xaction-for-edit)}
       [:td
        [:input {:type "text"
                 :size 2
                 :value (time/month (:date @xaction-for-edit))
                 :on-change
                 (fn [evt]
                   (let [new-month-val (-> evt .-target .-value)
                         new-date
                         (time/local-date
                          (time/year (:date @xaction-for-edit))
                          new-month-val
                          (time/day (:date @xaction-for-edit)))]
                     (swap! xaction-for-edit assoc :date new-date)))}]
        [:span "/"]
        [:input {:type "text"
                 :size 2
                 :value (time/day (:date @xaction-for-edit))
                 :on-change
                 (fn [evt]
                   (let [new-day-val (-> evt .-target .-value)
                         new-date
                         (time/local-date
                          (time/year (:date @xaction-for-edit))
                          (time/month (:date @xaction-for-edit))
                          new-day-val)]
                     (swap! xaction-for-edit assoc :date new-date)))
                 }]
        [:span "/"]
        [:input {:type "text"
                 :size 4
                 :value (time/year (:date @xaction-for-edit))
                 :on-change
                 (fn [evt]
                   (let [new-year-val (-> evt .-target .-value)
                         new-date
                         (time/local-date
                          new-year-val
                          (time/month (:date @xaction-for-edit))
                          new-year-val)]
                     (swap! xaction-for-edit assoc :date new-date)))}]]
       [:td [typeahead/typeahead-component
             {:ta-atom payee-ta-atom
              :query-func get-payees!
              :on-change (fn [selection]
                           (let [payee-name (:value selection)
                                 payee {:name payee-name
                                        :is-new (:is-new selection)
                                        :id (:id selection)}]
                             (swap! xaction-for-edit assoc :payee payee)
                             (reset! payee-ta-atom (merge
                                                    @payee-ta-atom
                                                    {:textbox-val payee-name}))))
              :item->text (fn [item]
                            (:name item))}]]
       [:td [typeahead/typeahead-component
             {:ta-atom ledger-ta-atom
              :query-func get-ledgers!
              :on-change (fn [selection]
                           (let [ledger-name (:value selection)
                                 ledger {:name ledger-name
                                         :is-new (:is-new selection)
                                         :id (:id selection)}]
                             (swap! xaction-for-edit assoc :ledger ledger)
                             (reset! ledger-ta-atom (merge
                                                    @ledger-ta-atom
                                                    {:textbox-val ledger-name}))))
              :item->text (fn [item]
                            (:name item))}]]
       [:td [:input {:type "text"
                     :value (:description @xaction-for-edit)
                     :on-change #(swap! xaction-for-edit assoc :description (-> % .-target .-value))}]]
       [:td [:input {:type "text"
                     :value (:amount @xaction-for-edit)
                     :on-change #(swap! xaction-for-edit assoc :amount (-> % .-target .-value))}]]
       [:td [:input {:type "checkbox"
                     :disabled
                     (not (:is-reconciled @xaction-for-edit))}]]
       [:td
        (if (int? (:backend-id @xaction-for-edit))
          ;; editing
          [:button.button.is-link
           {:on-click
            (fn [evt]
              (rf/dispatch [:edit nil])
              (let [xaction-to-save (xform-xaction-for-backend @xaction-for-edit)]
                (ajax/PUT
                 "/api/xactions/"
                 {:params {:xaction xaction-to-save}
                  :error-handler
                  (fn [err-data]
                    (.log js/console
                          "error on xaction save: "
                          (utils/pp err-data)))
                  :handler
                  (fn [_resp]
                    (.log js/console "xaction updated")
                    (rf/dispatch [:update-xaction @xaction-for-edit]))})))}
           "Save"]
          ;; adding
          [:button.button.is-link
           {:on-click
            (fn [_evt]
              (let [_ (pp/pprint {:xaction-to-add @xaction-for-edit})]
                (rf/dispatch [:set-last-date-used
                              (:date @xaction-for-edit)])
                (rf/dispatch [:add-transaction @xaction-for-edit])
                (let [xaction-to-add (-> @xaction-for-edit
                                         xform-xaction-for-backend)
                      _ (pp/pprint {:xaction-to-add xaction-to-add})]
                  (reset! xaction-for-edit (empty-xaction))
                  (ajax/POST
                   "/api/xactions/"
                   {:params {:xaction xaction-to-add}
                    :error-handler
                    (fn [err]
                      (.log js/console
                            "error on creation of new xaction: "
                            (utils/pp err))
                      (rf/dispatch [:remove-transaction
                                    (:uuid xaction-to-add)]))
                    :handler
                    (fn [_response]
                      (rf/dispatch [:transaction-fully-added
                                    (:uuid xaction-to-add)])
                      (.log js/console "success adding xaction")
                      (reset! payee-ta-atom (typeahead/new-typeahead-vals))
                      (reset! ledger-ta-atom (typeahead/new-typeahead-vals)))}))))
            }
           "Add"])
        ]])))

(defn view-xaction-row []
  (let [editing-id (rf/subscribe [:editing-id])]
    (fn [xaction]
     (let [#_ (.log js/console "xaction: " (utils/pp xaction))
           class (when (:add-waiting? xaction)
                   "rowhighlight")
           disable-edit-button?
           (or (:is-reconciled? xaction)
               (and @editing-id
                    (not= @editing-id (:uuid xaction))))]
       [:tr {:class class}
        [:td (time-fmt/unparse-local-date
              {:format-str "MM/dd/yyyy"}
              (:date xaction))]
        [:td (get-in xaction [:payee :name])]
        [:td (get-in xaction [:ledger :name])]
        [:td (:description xaction)]
        [:td (:amount xaction)]
        [:td
         [:input
          {:type "checkbox"
           :checked (:is-reconciled? xaction)
           :disabled (not (:is-reconciled? xaction))
           :on-change
           (fn [_evt]
             (let [is-checked? (-> _evt .-target .-checked)]
               (.log js/console (str "checked: " is-checked?))
               (if-not (= (not is-checked?) (:is-reconciled? xaction))
                 (throw
                  (ex-info
                   "should only be able to uncheck a reconciled xaction"
                   {:is-checked? is-checked?
                    :is-reconciled? (:is-reconciled? xaction)}))
                 (rf/dispatch [:unreconcile (:uuid xaction)]))))}]]
        [:td
         [:button.button.is-link
          {:on-click
           (fn [_evt]
             (rf/dispatch [:edit (:uuid xaction)]))
           :disabled disable-edit-button?}
          "Edit"]]]))))


(defn home-page []
  (let [xactions (rf/subscribe [:xactions-sorted-by-date-desc])
        is-reconciling? (rf/subscribe [:is-reconciling?])
        total (rf/subscribe [:total])
        editing-id (rf/subscribe [:editing-id])]
    (fn []
      [:div.container
       [:div.container
        [:div "Hello world, it is now"]
        [clock]]
       [:div.container
        [:nav.level
         [:div.level-left]
         [:div.level-right
          [:p.level-item
           [:button.button.is-primary.is-light
            {:on-click
             (fn [_evt]
               (rf/dispatch [:reconcile]))
             :disabled @is-reconciling?}
            "reconcile"]]
          [:p.level-item (str "$" @total)]]]
        [:table.table
         [:thead
          [:tr
           [:th "date"]
           [:th "payee"]
           [:th "ledger"]
           [:th "desc"]
           [:th "amount"]
           [:th "clear"]
           [:th "controls"]]]
         [:tbody
          (when-not @editing-id
            [editable-xaction-row (empty-xaction)])
          (doall
           (for [xaction @xactions]
             (let [is-editing-this-xaction? (= @editing-id (:uuid xaction))]
               (if is-editing-this-xaction?
                 ^{:key (:uuid xaction)} [editable-xaction-row xaction]
                 ^{:key (:uuid xaction)} [view-xaction-row xaction]))))]]]])))






(defn luminus-home-page []
  [:div.container
   (when-let [docs @(rf/subscribe [:docs])]
     [:div.row>div.col-sm-12
      [:div {:dangerouslySetInnerHTML
             {:__html (md->html docs)}}]])
   [:div.container
    "hi"]])

;; (def pages
;;   {:home #'home-page
;;    :lum-home #'luminus-home-page
;;    :about #'about-page/page
;;    ;; :login #'login-page/login-page
;;    })

(defonce match (r/atom nil))

(defn page []
  (let [user (rf/subscribe [:user])
        is-fetching-user? (rf/subscribe [:is-fetching-user?])
        #_ (pp/pprint {:user user
                      :is-fetching-user? is-fetching-user?})]
    ;; (.log js/console "user: " (utils/pp user))
    (fn []
     (if (and (not @user)
              (not @is-fetching-user?))
       [login-page/login-page]
       [:section.section.is-large
        [navbar]
        (when @match
          [(get-in @match [:data :view])])
        [:div.container "dater"
         [:ul
          [:li "page: " (get-in @match [:data :name])]
          [:li "user: " @user]
          [:li "fetching user: " (str @is-fetching-user?)]]]]))))

;; -------------------------
;; Routes

(def routes
  (reitit.frontend/router
   ["/"
    [""
     {:name ::home
      :view home-page
      :controllers [{:start #(.log js/console "controller - home - start")
                     :stop #(.log js/console "controller - home - stop")}]}]
    ["luminus-home"
     {:name ::luminus-home
      :view luminus-home-page
      :controllers [{:start #(.log js/console "controller - luminus-home - start")
                     :stop #(.log js/console "controller - luminus-home - stop")}]}]
    ["about"
     {:name ::about
      ;; :view about-page
      :view about-page/page
      :controllers [{:start #(.log js/console "controller - about - start")
                     :stop #(.log js/console "controller - about - stop")}]}]

    ["ledger-balances"
     {:name ::ledger-balances
      :view ledger-balances-page/page
      :controllers [{:start
                     (fn [& args]
                       (.log js/console "controller - bal - start")
                       (rf/dispatch [:fetch-ledger-totals]))
                     :stop #(.log js/console "controller - bal -stop")}]}]
    ]
   {:data {:controllers [{:start (.log js/console "controller - root - start")
                           :stop (.log js/console "controller - root - stop")}]
            :coercion rsc/coercion}}))

;; ;; -------------------------
;; ;; History
;; ;; must be called after routes have been defined
;; (defn hook-browser-navigation! []
;;   (doto (History.)
;;     (events/listen
;;       HistoryEventType/NAVIGATE
;;       (fn [event]
;;         (.log js/console "some kind of navigate event happening: " event)
;;         (secretary/dispatch! (.-token event))))
;;     (.setEnabled true))

;;   ;; (accountant/configure-navigation!
;;   ;;   {:nav-handler
;;   ;;    (fn [path]
;;   ;;      (.log js/console "accountant navigating to " path)
;;   ;;      (secretary/dispatch! path))
;;   ;;    :path-exists?
;;   ;;    (fn [path]
;;   ;;      (secretary/locate-route path))})
;;   ;; (accountant/dispatch-current!)
;;   )

;; -------------------------
;; Initialize app
;; (defn fetch-docs! []
;;   (GET "/docs" {:handler #(rf/dispatch [:set-docs %])}))

;; (defn mount-components []
;;   (rf/clear-subscription-cache!)
;;   (r/render [#'page] (.getElementById js/document "app")))

;; (defn init! []
;;   (rf/dispatch-sync [:initialize-db])
;;   (load-interceptors!)
;;   (fetch-docs!)
;;   (hook-browser-navigation!)
;;   (mount-components))


;; (defn home-page []
;;   [:div [:h2 "Welcome to Reagent!"]])

;; -------------------------
;; Initialize app

(defn ^:dev/after-load mount-root []
  (rf/dispatch-sync [:initialize-db])
  (re-frisk-remote/enable)
  (d/render [#'page] (.getElementById js/document "app")))

(defn ^:export ^:dev/once init! []
  (rfe/start!
   routes
   (fn [new-match]
     (swap! match (fn [old-match]
                    (let [_ (pp/pprint {:old-match old-match
                                        :new-match new-match})]
                     (when new-match
                       (assoc new-match :controllers (rfc/apply-controllers (:controllers old-match) new-match)))))))
   {:use-fragment true})
  (mount-root))


(comment

  (str (time/local-date 2023 7 18))

  (require '[cljs-time.coerce :as time-coerce])
  (time-coerce/to-local-date "2023-07-18")

  )
