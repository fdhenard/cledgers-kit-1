(ns fdhenard.cledgers.env
  (:require
    [clojure.tools.logging :as log]
    [fdhenard.cledgers.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init       (fn []
                 (log/info "\n-=[cledgers starting using the development or test profile]=-"))
   :start      (fn []
                 (log/info "\n-=[cledgers started successfully using the development or test profile]=-"))
   :stop       (fn []
                 (log/info "\n-=[cledgers has shut down successfully]=-"))
   :middleware wrap-dev
   :opts       {:profile       :dev
                :persist-data? true}})
