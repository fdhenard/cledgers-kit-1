(ns fdhenard.cledgers.web.routes.api
  (:require
   [clojure.pprint :as pp]
   [integrant.core :as ig]
   [buddy.hashers :as hashers]
   [reitit.coercion.malli :as malli]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.swagger :as swagger]
   [fdhenard.cledgers.web.routes.utils :as route-utils]
   [fdhenard.cledgers.web.controllers.health :as health]
   [fdhenard.cledgers.web.middleware.exception :as exception]
   [fdhenard.cledgers.web.middleware.formats :as formats]))

(def route-data
  {:coercion   malli/coercion
   :muuntaja   formats/instance
   :swagger    {:id ::api}
   :middleware [;; query-params & form-params
                parameters/parameters-middleware
                  ;; content-negotiation
                muuntaja/format-negotiate-middleware
                  ;; encoding response body
                muuntaja/format-response-middleware
                  ;; exception handling
                coercion/coerce-exceptions-middleware
                  ;; decoding request body
                muuntaja/format-request-middleware
                  ;; coercing response bodys
                coercion/coerce-response-middleware
                  ;; coercing request parameters
                coercion/coerce-request-middleware
                  ;; exception handling
                exception/wrap-exception]})

;; Routes
(defn api-routes [_opts]
  [["/swagger.json"
    {:get {:no-doc  true
           :swagger {:info {:title "fdhenard.cledgers API"}}
           :handler (swagger/create-swagger-handler)}}]
   ["/health"
    {:get health/healthcheck!}]
   ["/login/"
    {:post {:handler (fn [{:keys [body-params session] :as _req}]
                       (let [#_ (pp/pprint {:body-params (:body-params req)})
                             #_ (pp/pprint {:req req})
                             {:keys [query-fn]} _opts
                             #_ (pp/pprint {:_opts _opts})
                             user (query-fn :get-user-by-uname {:username (:username body-params)})]
                         (if-not (and user (hashers/check (:password body-params) (:pass user)))
                           {:status 403}
                           (let [user-res (dissoc user :pass)]
                             {:status 200
                              :session (assoc session :identity user-res)
                              :body user-res}))))}}]])

(derive :reitit.routes/api :reitit/routes)

(defmethod ig/init-key :reitit.routes/api
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  [base-path route-data (api-routes opts)])
