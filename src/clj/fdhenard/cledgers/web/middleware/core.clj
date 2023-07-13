(ns fdhenard.cledgers.web.middleware.core
  (:require
   [clojure.pprint :as pp]
   [fdhenard.cledgers.env :as env]
    [ring.middleware.defaults :as defaults]
    [ring.middleware.session.cookie :as cookie]
    [buddy.auth :as buddy-auth]
    [buddy.auth.accessrules :as ba-accessrules]
    [buddy.auth.middleware :as ba-middleware]
    [buddy.auth.backends.session :as ba-backend-sess]))

(def rules
  [{:uris ["/api/login/"
           "/api/logout/"]
    :handler (constantly true)}
   {:uri "/api/*"
    :handler buddy-auth/authenticated?}
   ])

(defn on-error [request value]
  {:status 403
   :headers {}
   :body "Not authorized"})

(defn wrap-base
  [{:keys [metrics site-defaults-config cookie-secret] :as opts}]
  (let [cookie-store (cookie/cookie-store {:key (.getBytes ^String cookie-secret)})]
    (fn [handler]
      (cond-> ((:middleware env/defaults) handler opts)
        true (ba-accessrules/wrap-access-rules {:rules rules
                                                :on-error on-error})
        true (ba-middleware/wrap-authentication (ba-backend-sess/session-backend))
        true (defaults/wrap-defaults
              (assoc-in site-defaults-config [:session :store] cookie-store))
        ))))
