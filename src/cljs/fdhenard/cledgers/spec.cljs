(ns fdhenard.cledgers.spec)

(def Transaction
  [:map
   [:uuid string?]
   [:date
    [:map
     [:month int?]
     [:day int?]
     [:year int?]]]
   [:description string?]
   [:amount string?]
   [:add-waiting? {:optional true} boolean?]
   [:payee
    [:map
     [:name string?]
     [:id string?]]]
   [:ledger
    [:map
     [:name string?]
     [:id string?]]]])
