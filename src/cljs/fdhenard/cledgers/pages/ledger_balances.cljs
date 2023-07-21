(ns fdhenard.cledgers.pages.ledger-balances
  (:require [re-frame.core :as rf]))


(defn page []
  (let [ledger-totals (rf/subscribe [:ledger-totals])]
    (fn []
     [:div.container
      [:table.table
       [:thead
        [:tr
         [:th "name"]
         [:th "amount"]]]
       [:tbody
        (doall
         (for [ledg-tot @ledger-totals]
           ^{:key (:ledger_id ledg-tot)}
           [:tr
            [:td (:ledger_name ledg-tot)]
            [:td (:total ledg-tot)]]))]]])))
