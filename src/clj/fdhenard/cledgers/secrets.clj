(ns fdhenard.cledgers.secrets
  (:require [clojure.pprint :as pp]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [integrant.core :as ig])
  (:import [io.akeyless.client Configuration]
           [io.akeyless.client.model Configure ConfigureOutput ListItems ListItemsInPathOutput GetSecretValue]
           [io.akeyless.client.api V2Api]))


(def api (atom nil))

(defn get-akeyless-api []
  (if @api
    @api
    (let [client (doto (Configuration/getDefaultApiClient)
                   (.setBasePath "https://api.akeyless.io"))
          _api (V2Api. client)
          _ (reset! api _api)]
      _api)))

(defn get-token []
  (let [akeyless-access-id (System/getenv "AKEYLESS_ACCESS_ID")
        akeyless-access-key (System/getenv "AKEYLESS_ACCESS_KEY")
        _ (log/debug
           (with-out-str
             (pp/pprint
              {:akeyless
               {:access-id-has-value? (not (string/blank? akeyless-access-id))
                :access-key-has-value? (not (string/blank? akeyless-access-key))}})))
        api (get-akeyless-api)
        body (doto (Configure.)
               (.accessId akeyless-access-id)
               (.accessKey akeyless-access-key))
        ^ConfigureOutput out (.configure api body)]
    (.getToken out)))

(comment

  (into [1 2] [3 4])

  (string/last-index-of "hi/one/two" "/")
  ;; (subs "hi/one/two" (string/last-index-of))

  )

(defn str-path->kw-path [path-str]
  (let [path-str$ (subs path-str 1)]
    (->> (string/split path-str$ #"/")
         (mapv keyword))))

(defn list-items [env-kw]
  (let [_ (log/debug "fetching secrets")
        ^ListItems list-body (doto (ListItems.)
                               (.setToken (get-token)))
        
        ^ListItemsInPathOutput list-out (.listItems @api list-body)]
    (->> (.getItems list-out)
         (map (fn [item-obj]
                (let [path-str (.getItemName item-obj)]
                  {:path (str-path->kw-path path-str)
                   :path-str path-str
                   :item-obj item-obj})))
         (filter (fn [item]
                   (let [base-path (vec (take 2 (:path item)))]
                     (= [:cledgers env-kw] base-path)))))))

(comment


  (def items (list-items :prod))
  items
  (map :path items)
  (type items)
  (count items)
  (:folders items)
  (map #(.getItemName %) (:items items))
  (vec items)
  (def item (.get items 0))
  item
  (bean item)
  (println (str item))
  (.getItemName item)
  ;; => "/location/test2"

  )

(defn get-secret-values [items]
  (let [_ (log/debug "fetching secret values")
        ^GetSecretValue gsv-body
        (doto (GetSecretValue.)
          (.setToken (get-token))
          (.setNames (java.util.ArrayList. (map :path-str items))))]
    (.getSecretValue @api gsv-body)))

(comment

  (def sv (get-secret-values items))
  (type sv)
  sv
  (get sv "/cledgers/prod/db/hostname")

  (-> {}
      (assoc-in [:one :two] 3)
      (assoc-in [:one :four] 5)
      (assoc-in [:one :two :three] 4))


  )

(defn add-secret-vals [items]
  (let [secret-vals (get-secret-values items)]
    (map (fn [item]
           (let [val (get secret-vals (:path-str item))]
             (assoc item :val val)))
         items)))

(comment

  (map #(dissoc % :item-obj) (add-secret-vals items))

  )

(defn get-all-secrets [env-kw]
  (let [items (->> (list-items env-kw)
                   add-secret-vals)]
   (reduce
    (fn [accum {:keys [path val] :as _item}]
      (assoc-in accum (drop 2 path) val))
    {}
    items)))


(comment

  (get-all-secrets :prod)

  )

(def secrets-atom (atom nil))
(def secrets-fetch-delay-in-millis (* 1000 60 5))

(defn fetching-process! [env-kw initial-delay-in-millis]
  (when (and initial-delay-in-millis
             (> initial-delay-in-millis 0))
    (Thread/sleep initial-delay-in-millis))
  (while true
    (reset! secrets-atom (get-all-secrets env-kw))
    (Thread/sleep secrets-fetch-delay-in-millis)))

(def fetching-process-future-atom (atom nil))

(defn fetching-process-active? []
  (and (not (nil? @fetching-process-future-atom))
       (not (future-done? @fetching-process-future-atom))))

(defn startup! [env-kw & {:keys [initial-delay-in-millis] :as _opts}]
  (if (fetching-process-active?)
    (log/warn "secrets fetching process has already started")
    (do
      (reset! fetching-process-future-atom
              (future (fetching-process! env-kw initial-delay-in-millis)))
      (if (fetching-process-active?)
        (log/info "secrets fetching process started")
        (log/warn "secrets fetching process not started")))))

(comment

  @secrets-atom
  ;; (def secrets-process (startup! :prod))
  (startup! :prod)
  @fetching-process-future-atom
  (future-done? @fetching-process-future-atom)
  (realized? @fetching-process-future-atom)
  (future-cancel @fetching-process-future-atom)


  )

(defmethod ig/init-key :secrets/all-secrets
  [_ {:keys [env] :as _opts}]
  #_{:opts opts}
  (when-not (fetching-process-active?)
    (reset! secrets-atom (get-all-secrets env))
    (startup! env {:initial-delay-in-millis secrets-fetch-delay-in-millis}))
  @secrets-atom)

(defmethod ig/halt-key! :secrets/all-secrets
  [_ _]
  (when (fetching-process-active?)
    (future-cancel @fetching-process-future-atom)
    (if (future-done? @fetching-process-future-atom)
      (log/info "secrets fetching process halted")
      (log/warn "error halting secrets fetching process"))))

(defmethod ig/init-key :secrets.db/hostname
  [_ {:keys [all-secrets] :as _opts}]
  (get-in all-secrets [:db :hostname]))
(defmethod ig/init-key :secrets.db/password
  [_ {:keys [all-secrets] :as _opts}]
  (get-in all-secrets [:db :password]))
(defmethod ig/init-key :secrets.db/port
  [_ {:keys [all-secrets] :as _opts}]
  (get-in all-secrets [:db :port]))
(defmethod ig/init-key :secrets.db/username
  [_ {:keys [all-secrets] :as _opts}]
  (get-in all-secrets [:db :username]))

(comment

  (require '[integrant.repl.state :as state])
  state/system


  )
