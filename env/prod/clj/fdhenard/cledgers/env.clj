(ns fdhenard.cledgers.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[cledgers starting]=-"))
   :start      (fn []
                 (log/info "\n-=[cledgers started successfully]=-"))
   :stop       (fn []
                 (log/info "\n-=[cledgers has shut down successfully]=-"))
   :middleware (fn [handler _] handler)
   :opts       {:profile :prod}})
