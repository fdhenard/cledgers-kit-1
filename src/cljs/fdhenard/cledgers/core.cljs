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
            [reitit.coercion.schema :as rsc])
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


(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     #_[:img {:src (str js/context "/img/warning_clojure.png")}]
     [:img {:src "/img/warning_clojure.png"}]
     #_"warning! clojure"]]])




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

(def last-date-used (atom (time/today)))

(comment

  @last-date-used
  (str @last-date-used)
  (type @last-date-used)

  #_(require '[cljs-time.format :as time-fmt])
  (def local-dt-fmt (time-fmt/formatter "yyyy-MM-dd"))
  local-dt-fmt

  (time-fmt/unparse-local-date {:formatters [local-dt-fmt]} @last-date-used)

  (time-fmt/unparse-local-date {:format-str "yyyy-MM-dd"} @last-date-used)


  )

(defn empty-xaction [] {:uuid (str (uuid/make-random-uuid))
                        #_#_:date {:month (time/month @last-date-used)
                               :day (time/day @last-date-used)
                               :year (time/year @last-date-used)}
                        ;; :date (time-fmt/unparse-local-date
                        ;;        {:format-str "yyyy-MM-dd"}
                        ;;        @last-date-used)
                        :date @last-date-used
                        :description ""
                        :amount ""
                        :add-waiting? true
                        :is-reconciled? false})

(defn xform-xaction-for-backend [xaction]
  (-> xaction
    (dissoc :add-waiting?)
    #_(assoc :date (time-fmt/unparse-local-date
                  {:format-str "yyyy-MM-dd"}
                  (:date xaction)))))

(defn get-payees! [q-str callback]
  #_(println "callback:" callback)
  (let [response->results
        (fn [response]
          (let [payees (-> response :result)
                #_ (println "something:")
                #_ (pp/pprint something)]
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

(defn new-xaction-row []
  (let [new-xaction (r/atom (empty-xaction))
        payee-ta-atom (r/atom (typeahead/new-typeahead-vals))
        ledger-ta-atom (r/atom (typeahead/new-typeahead-vals))]
    (fn []
      [:tr {:key "new-one"}
       [:td
        [:input {:type "text"
                 :size 2
                 ;; :value (get-in @new-xaction [:date :month])
                 :value (time/month (:date @new-xaction))
                 ;; :on-change #(swap! new-xaction assoc-in [:date :month] (-> % .-target .-value))
                 :on-change
                 (fn [evt]
                   (let [new-month-val (-> evt .-target .-value)
                         new-date
                         (time/local-date
                          (time/year (:date @new-xaction))
                          new-month-val
                          (time/day (:date @new-xaction)))]
                     (swap! new-xaction assoc :date new-date)))}]
        [:span "/"]
        [:input {:type "text"
                 :size 2
                 ;; :value (get-in @new-xaction [:date :day])
                 :value (time/day (:date @new-xaction))
                 ;; :on-change #(swap! new-xaction assoc-in [:date :day] (-> % .-target .-value))
                 :on-change
                 (fn [evt]
                   (let [new-day-val (-> evt .-target .-value)
                         new-date
                         (time/local-date
                          (time/year (:date @new-xaction))
                          (time/month (:date @new-xaction))
                          new-day-val)]
                     (swap! new-xaction assoc :date new-date)))
                 }]
        [:span "/"]
        [:input {:type "text"
                 :size 4
                 ;; :value (get-in @new-xaction [:date :year])
                 :value (time/year (:date @new-xaction))
                 ;; :on-change #(swap! new-xaction assoc-in [:date :year] (-> % .-target .-value))
                 :on-change
                 (fn [evt]
                   (let [new-year-val (-> evt .-target .-value)
                         new-date
                         (time/local-date
                          new-year-val
                          (time/month (:date @new-xaction))
                          new-year-val)]
                     (swap! new-xaction assoc :date new-date)))}]]
       [:td [typeahead/typeahead-component
             {:ta-atom payee-ta-atom
              :query-func get-payees!
              :on-change (fn [selection]
                           (let [payee-name (:value selection)
                                 payee {:name payee-name
                                        :is-new (:is-new selection)
                                        :id (:id selection)}]
                             (swap! new-xaction assoc :payee payee)
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
                             (swap! new-xaction assoc :ledger ledger)
                             (reset! ledger-ta-atom (merge
                                                    @ledger-ta-atom
                                                    {:textbox-val ledger-name}))))
              :item->text (fn [item]
                            (:name item))}]]
       [:td [:input {:type "text"
                     :value (:description @new-xaction)
                     :on-change #(swap! new-xaction assoc :description (-> % .-target .-value))}]]
       [:td [:input {:type "text"
                     :value (:amount @new-xaction)
                     :on-change #(swap! new-xaction assoc :amount (-> % .-target .-value))}]]
       [:td "false"]
       [:td
        [:button
         {:on-click
          (fn [_evt]
            (let [xaction-to-add (-> @new-xaction
                                     xform-xaction-for-backend)
                  _ (pp/pprint {:xaction-to-add xaction-to-add})]
              (reset! last-date-used (:date @new-xaction))
              (rf/dispatch [:add-transaction xaction-to-add])
              (reset! new-xaction (empty-xaction))
              (ajax/POST
               "/api/xactions/"
               {:params
                {:xaction
                 (assoc
                  xaction-to-add
                  :date
                  (time-fmt/unparse-local-date
                   {:format-str "yyyy-MM-dd"}
                   (:date xaction-to-add)))}
                :error-handler
                (fn [err]
                  (.log js/console "error: " (utils/pp err))
                  (rf/dispatch [:remove-transaction (:uuid xaction-to-add)]))
                :handler
                (fn [_response]
                  (rf/dispatch [:transaction-fully-added (:uuid xaction-to-add)])
                  (.log js/console "success adding xaction")
                  (reset! payee-ta-atom (typeahead/new-typeahead-vals))
                  (reset! ledger-ta-atom (typeahead/new-typeahead-vals)))})))
          }
         "Add"]]])))


(defn home-page []
  (let [xactions (rf/subscribe [:xactions])
        is-reconciling? (rf/subscribe [:is-reconciling?])
        total (rf/subscribe [:total])]
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
        [new-xaction-row]
        (for [[_ xaction] @xactions]
          (let [#_ (.log js/console "xaction: " (utils/pp xaction))
                class (when (:add-waiting? xaction)
                        "rowhighlight")]
            [:tr {:key (:uuid xaction)
                  :class class}
             [:td (time-fmt/unparse-local-date
                   {:format-str "MM/dd/yyyy"}
                   (:date xaction))]
             [:td (get-in xaction [:payee :name])]
             [:td (get-in xaction [:ledger :name])]
             [:td (:description xaction)]
             [:td (:amount xaction)]
             [:td (str (:is-reconciled? xaction))]]))]]]])))






(defn luminus-home-page []
  [:div.container
   (when-let [docs @(rf/subscribe [:docs])]
     [:div.row>div.col-sm-12
      [:div {:dangerouslySetInnerHTML
             {:__html (md->html docs)}}]])])

(def pages
  {:home #'home-page
   :lum-home #'luminus-home-page
   :about #'about-page
   ;; :login #'login-page/login-page
   })

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
      :controllers [{:start (.log js/console "controller - home - start")
                     :stop (.log js/console "controller - home - stop")}]}]
    ["luminus-home"
     {:name ::luminus-home
      :view luminus-home-page
      :controllers [{:start (.log js/console "controller - luminus-home - start")
                     :stop (.log js/console "controller - luminus-home - stop")}]}]
    ["about"
     {:name ::about
      :view about-page
      :controllers [{:start (.log js/console "controller - about - start")
                     :stop (.log js/console "controller - about - stop")}]}]]
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
  (rf/dispatch [:fetch-user])
  (rf/dispatch [:fetch-transactions])
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
