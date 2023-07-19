(ns fdhenard.cledgers.spec)

(def Transaction
  [:map
   [:uuid string?]
   [:date any?]
   [:description string?]
   [:amount string?]
   [:add-waiting? {:optional true} boolean?]
   [:payee
    [:map
     [:name string?]
     [:id int?]]]
   [:ledger
    [:map
     [:name string?]
     [:id int?]]]
   [:is-reconciled? boolean?]])
