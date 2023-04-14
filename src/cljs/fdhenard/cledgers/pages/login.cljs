(ns fdhenard.cledgers.pages.login
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]
            [fdhenard.cledgers.utils :as utils]
            [reagent.core :as r]))

(defn login [username password]
  (.log js/console "logging in")
  (ajax/POST "/api/login/"
             {:params {:username @username :password @password}
              :error-handler #(.log js/console "error" (utils/pp %))
              :handler #(do
                          ;; (.log js/console "res:" (utils/pp %))
                          (rf/dispatch [:login %]))}))

(defn login-page []
  (let [username (r/atom "")
        passwd (r/atom "")]
    (fn []
      [:div.container
       [:div.row
        [:div.col-md-6.col-md-offset-3
         [:div.panel.panel-default
          [:div.panel-body
           [:div.form-group
            [:label {:for "username"} "Username"]
            [:input.form-control {:type "text"
                                  :value @username
                                  :on-change #(reset! username (-> % .-target .-value))
                                  :id "username"}]
            [:label {:for "password"} "Password"]
            [:input.form-control {:type "password"
                                  :value @passwd
                                  :on-change #(reset! passwd (-> % .-target .-value))
                                  :id "password"}]]
           [:button.btn.btn-primary {:on-click #(login username passwd)} "login"]]]]]]
      ;; [:div.container
      ;;  [:input {:type "text"
      ;;           :value @username
      ;;           :on-change #(reset! username (-> % .-target .-value))}]
      ;;  [:input {:type "password"
      ;;           :value @passwd
      ;;           :on-change #(reset! passwd (-> % .-target .-value))}]
      ;;  [:button {:on-click #(login username passwd)} "login"]]
      )))
