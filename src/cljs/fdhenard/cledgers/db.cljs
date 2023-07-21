(ns fdhenard.cledgers.db
  (:require [cljs-time.core :as time]))

(def default-db
  {:page :home
   :time (js/Date.)
   :time-color "#f88"
   ;; :xaction-editing nil
   :xactions {}
   ;; :user (js->clj js/user :keywordize-keys true)
   :user nil
   #_#_:is-fetching-user? true
   :last-date-used (time/today)
   })
