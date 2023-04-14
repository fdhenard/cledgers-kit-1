(ns fdhenard.cledgers.db)

(def default-db
  {:page :home
   :time (js/Date.)
   :time-color "#f88"
   ;; :xaction-editing nil
   :xactions {}
   :user (js->clj js/user :keywordize-keys true)
   })
