(ns fdhenard.cledgers.web.routes.api
  (:require
   [clojure.pprint :as pp]
   [clojure.tools.logging :as log]
   [java-time :as time]
   [integrant.core :as ig]
   [buddy.hashers :as hashers]
   [next.jdbc :as jdbc]
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


(defn handle-post-xactions [{:keys [connection query-fn] :as _opts}]
  (fn [request]
   (jdbc/with-transaction [tx-conn connection]
     (let [user-id (get-in request [:session :identity :id])
           xaction (get-in request [:body-params :xaction])
           #_ (pp/pprint {:xaction xaction})
           #_ (pp/pprint {:request request})
           payee (:payee xaction)
           ledger (:ledger xaction)
           payee-id
           (if-not (:is-new payee)
             (:id payee)
             (let [payee (assoc payee :created-by-id user-id)
                   create-res (query-fn tx-conn :create-payee! payee)]
               (:id create-res)))
           ledger-id
           (if-not (:is-new ledger)
             (:id ledger)
             (let [ledger (assoc ledger :created-by-id user-id)
                   create-res (query-fn tx-conn :create-ledger! ledger)]
               (:id create-res)))
           yr (get-in xaction [:date :year])
           mo (get-in xaction [:date :month])
           da (get-in xaction [:date :day])
           _ (pp/pprint {:yr yr :mo mo :da da})
           new-date (time/local-date yr mo da)
           updated-xaction (-> xaction
                               (dissoc :payee)
                               (dissoc :ledger)
                               (merge {:date new-date
                                       :amount (-> xaction :amount bigdec)
                                       :created-by-id user-id
                                       :payee-id payee-id
                                       :ledger-id ledger-id}))
           _ (log/debug "new-xaction:" (pp/pprint {:updated-xaction  updated-xaction}))
           #_ (log/debug (str "xactions post request:\n"
                              (utils/pp {:request request})))
           _ (query-fn tx-conn :create-xaction! updated-xaction)]
       {:status 200}))))


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
                              :body user-res}))))}}]
   ["/user"
    {:get {:handler (fn [request]
                      (let [user (get-in request [:session :identity])]
                        (if-not user
                          {:status 401}
                          {:status 200
                           :body user})))}}]
   ["/payees"
    {:get {:handler (fn [request]
                      (let [{:keys [query-fn]} _opts
                            q-parm (-> request :params :q)
                            result (query-fn :get-payees {:q (str q-parm "%")})]
                        {:status 200
                         :body {:result result}}))}}]
   ["/ledgers"
    {:get {:handler (fn [request]
                      (let [{:keys [query-fn]} _opts
                            q-parm (get-in request [:params :q])]
                        {:status 200
                         :body {:result (query-fn :get-ledgers {:q (str q-parm "%")})}}))}}]
   ["/xactions/"
    {:post {:handler (handle-post-xactions _opts)}
     :get {:handler
           (fn [_request]
             (let [{:keys [query-fn]} _opts
                   xactions (->> (query-fn :get-transactions {})
                                 (map (fn [xaction]
                                        #_(pp/pprint {:xaction xaction})
                                        (assoc xaction :amount (str (:amount xaction))))))]
               {:status 200
                :body {:result xactions}}))}}]])

(derive :reitit.routes/api :reitit/routes)

(defmethod ig/init-key :reitit.routes/api
  [_ {:keys [base-path]
      :or   {base-path ""}
      :as   opts}]
  [base-path route-data (api-routes opts)])
