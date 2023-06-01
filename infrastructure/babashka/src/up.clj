(ns up
  (:require
   [clojure.pprint :as pp]
   [clojure.edn :as edn]
   [clojure.set :as set]
   [clojure.string :as string]
   [babashka.pods :as pods]
   [cheshire.core :as json]))

(pods/load-pod 'epiccastle/bbssh "0.4.0")

(require '[pod.epiccastle.bbssh.core :as bbssh])
;; (require '[pod.epiccastle.bbssh.agent :as agent])

(defn config* []
  (let [all-config (-> (System/getenv "BB_IAC_CONFIG_PATH")
                       slurp
                       edn/read-string)
        cledgers-inventory (get-in all-config [:inventory :cledgers])]
    (assoc all-config :inventory cledgers-inventory)))

(defn private-key-path* []
  (or (System/getenv "SSH_PRIVATE_KEY_PATH")
      (let [user-home (System/getProperty "user.home")]
        (str user-home "/.ssh/id_ed25519"))))

(comment

  (private-key-path*)

  (config*)

  (System/getenv "BB_IAC_CONFIG_PATH")


  )

;; (agent/set-debug-fn
;;  (fn [_level message]
;;    (binding [*out* *err*]
;;      (println message))))

(defn sshesh* []
  (bbssh/ssh (get-in (config*) [:inventory :hostname])
             {:username (get-in (config*) [:inventory :username])
              :identity (private-key-path*)
              :accept-host-key :new}))

(defn ssh-exec [sshesh cmd-str]
  (-> sshesh
      (bbssh/exec cmd-str
                  {:out :string :err :string})
      deref))

(comment

  ;; in a terminal
  ;; bb nrepl-server 1667
  ;; in emacs
  ;; M-x cider-connect <enter> localhost <enter> 1667 <enter>

  (try
    (-> (sshesh*)
        (ssh-exec "echo 'I am running remotely'")
        :out
        )
    (catch clojure.lang.ExceptionInfo ei
      (pp/pprint {:ei-data (ex-data ei)})
      (println (ex-message ei))))



  )

(defn file-exists? [sshesh config]
  (let [res (ssh-exec sshesh (str "ls " (:dest config)))]
    (case (:exit res)
      0 true
      2 false
      (throw (ex-info "unexpected :exit from exec" {:res res})))))

(comment

  (def sshesh (sshesh*))
  (file-exists? sshesh {:dest "/bogus"})
  (file-exists? sshesh {:dest "/tmp/dokku_bootstrap.sh"})
  


  )

(defn get-url! [sshesh config]
  (when-not (file-exists? sshesh config)
    (let [res (ssh-exec sshesh (str "wget -NO " (:dest config) " " (:url config)))
          _ (when-not (= 0 (:exit res))
              (throw (ex-info "unexpected :exit val from exec" {:res res})))])))

(comment

  (get-url! sshesh
            {:dest "/tmp/dokku_bootstrap.sh"
             :url "https://dokku.com/install/v0.30.6/bootstrap.sh"})


  )

(defn is-dokku-installed? [sshesh]
  (let [cmd-res (ssh-exec sshesh "dokku version")
        the-re #"^dokku version (\S+)\n"
        matches (re-matches the-re (:out cmd-res))
        #_ (pp/pprint {:cmd-res cmd-res
                      :matches matches})]
    (and (= 0 (:exit cmd-res))
         (not (nil? matches))
         (= 2 (count matches)))))

(comment

  (re-matches #"^dokku version (\S+)" "something")
  (re-matches #"^dokku version (\S+)\n" "dokku version 88.88.88\n")

  (def sshesh (sshesh*))
  (is-dokku-installed? sshesh)



  )

(defn get-dokku-ssh-keys [sshesh]
  (let [cmd-res (ssh-exec sshesh (str "dokku ssh-keys:list --format json"))]
    (cond
      (= 0 (:exit cmd-res))
      (-> cmd-res :out (json/parse-string true))
      (and (= 1 (:exit cmd-res))
           (let [out-lc (-> cmd-res :err string/lower-case)]
             (string/includes? out-lc "no public keys found")))
      []
      :else (throw (ex-info "unexpected result" {:cmd-res cmd-res})))))

(comment

  (def sshesh (sshesh*))
  (get-dokku-ssh-keys sshesh)

  (seq (:ssh-pub-keys (config*)))

  )

(defn add-dokku-ssh-admin-key! [sshesh key-id]
  (let [config-key-id (keyword (subs (name key-id) 6)) ;; remove 'admin-'
        pubkey (get-in (config*) [:ssh-pub-keys config-key-id])
        _ (when-not pubkey
            (throw (ex-info "pubkey not found" {:config-key-id config-key-id})))
        res (ssh-exec sshesh (str "echo \"" pubkey "\" | sudo dokku ssh-keys:add " (name key-id)))
        #_ (pp/pprint {:res res})
        _ (when (= 1 (:exit res))
            (throw (ex-info "error adding key" {:cmd-res res})))]))

(defn remove-dokku-ssh-admin-key! [sshesh key-id]
  (let [res (ssh-exec sshesh (str "sudo dokku ssh-keys:remove " (name key-id)))
        _ (when (= 1 (:exit res))
            (throw (ex-info "error removing key" {:cmd-res res})))]))

(comment

  (def sshesh (sshesh*))
  (subs "admin-test" 6)

  )

(defn sync-dokku-ssh-admin-keys! [sshesh]
  (let [existing-keys (get-dokku-ssh-keys sshesh)
        existing-keynames (->> existing-keys
                               (map :name)
                               (map keyword)
                               set)
        available-keynames (->> (seq (:ssh-pub-keys (config*)))
                                (map first)
                                (map name)
                                (map #(str "admin-" %))
                                (map keyword)
                                set)
        keynames-to-add (set/difference available-keynames existing-keynames)
        _ (doseq [keyname keynames-to-add]
            (add-dokku-ssh-admin-key! sshesh keyname))
        keynames-to-delete (set/difference existing-keynames available-keynames)
        _ (doseq [keyname keynames-to-delete]
            (remove-dokku-ssh-admin-key! sshesh keyname))]))

(comment

  (config*)

  )

(defn get-global-domain [sshesh]
  (let [$ (ssh-exec sshesh "dokku domains:report --global")
        out-lines (-> $ :out (string/split #"\n"))
        line-2 (nth out-lines 1)
        line-2-re #"^\s+Domains global enabled:\s+(false|true)\s+$"
        [_ domains-global-enabled-str] (re-matches line-2-re line-2)
        domains-global-enabled? (Boolean/valueOf domains-global-enabled-str)]
    (when domains-global-enabled?
      (let [line-3 (nth out-lines 2)
            line-3-re #"^\s+Domains global vhosts:\s+(\S+)\s+$"
            [_ vhost] (re-matches line-3-re line-3)]
        vhost))))

(defn set-global-domain! [sshesh]
  (let [cmd-res (ssh-exec sshesh (str "dokku domains:set-global " (get-in (config*) [:inventory :hostname])))
        #_ (pp/pprint {:cmd-res cmd-res})
        _ (when (= 1 (:exit cmd-res))
            (throw (ex-info "err setting global domain" {:cmd-res cmd-res})))]))

(comment

  (def sshesh (sshesh*))
  (get-global-domain sshesh)

  (def line2 "       Domains global enabled:        false                    ")
  (def the-re #"^\s+Domains global enabled:\s+(false|true)\s+$")
  (re-matches the-re line2)

  (boolean "false")
  (Boolean/valueOf "false")

  (get-global-domain sshesh)

  (set-global-domain! sshesh)

  )

(defn is-postgresql-plugin-installed? [sshesh]
  (let [cmd-res (ssh-exec sshesh "dokku plugin:list")
        plugin-out-lines (-> cmd-res
              :out
              (string/split #"\n"))
        the-re #"^\s{2}(\S+)\s+.+"
        line->plugin-name
        (fn [line]
          (let [matches (re-matches the-re line)
                _ (when-not matches
                    (throw (ex-info "unexpected result" {:line line})))]
            (nth matches 1)))
        installed-plugins (->> plugin-out-lines
                               (map line->plugin-name)
                               set)]
    (contains? installed-plugins "postgres")))



(comment

  (is-postgresql-plugin-installed? (sshesh*))

  (def test-str "  00_dokku-standard    0.30.6 enabled    dokku core standard plugin")
  (def the-re #"^\s{2}(\S+)\s+.+")
  (re-matches the-re test-str)

  )

(defn install-postgresql-plugin! [sshesh]
  (let [cmd-res (ssh-exec sshesh "sudo dokku plugin:install https://github.com/dokku/dokku-postgres.git")
        _ (when-not (= 0 (:exit cmd-res))
            (throw (ex-info "error installing postgresql plugin" {:cmd-res cmd-res})))]))

(comment

  (install-postgresql-plugin! (sshesh*))

  )

(defn does-pg-service-exist? [sshesh pg-svc-name]
  (let [cmd-res (ssh-exec sshesh (str "dokku postgres:exists " pg-svc-name))]
    (= 0 (:exit cmd-res))))

(def pg-service-name "cledgers")

(comment

  (does-pg-service-exist? (sshesh*) pg-service-name)

  )

(defn install-pg-service! [sshesh pg-svc-name]
  (let [cmd-res (ssh-exec sshesh (str "dokku postgres:create " pg-svc-name))
        _ (when-not (= 0 (:exit cmd-res))
            (throw (ex-info "error insalling pg svc" {:cmd-res cmd-res})))]))

(comment

  (install-pg-service! (sshesh*) pg-service-name)

  )

(defn get-pg-exposed-ports [sshesh svc-name]
  (let [cmd-res (ssh-exec sshesh (str "dokku postgres:info " svc-name))
        out-lines (string/split (:out cmd-res) #"\n")
        exposed-port-line-idx
        (loop [idx 0]
          (if (> idx (count out-lines))
            nil
            (let [curr-line (nth out-lines idx)]
              (if (string/includes? (string/lower-case curr-line) "exposed ports:")
                idx
                (recur (inc idx))))))
        _ (when (nil? exposed-port-line-idx)
            (throw (ex-info "exposed ports line not found" {:out-lines out-lines})))
        exposed-port-line (nth out-lines exposed-port-line-idx)
        the-re #"^\s{7}Exposed ports:\s{7}(\S+)\s+$"
        matches (re-matches the-re exposed-port-line)]
    (nth matches 1)))

(comment

  (get-pg-exposed-ports (sshesh*) pg-service-name)

  (def the-re #"^\s{7}Exposed ports:\s{7}(\S+)\s+$")
  (def test-line "       Exposed ports:       -                        ")
  (re-matches the-re test-line)

  )

(defn expose-pg-port [sshesh svc-name target-port]
  (let [cmd-res (ssh-exec sshesh (str "dokku postgres:expose " svc-name " " target-port))
        _ (when-not (= 0 (:exit cmd-res))
            (throw (ex-info "error exposing port" {:cmd-res cmd-res})))]))

(comment

  (expose-pg-port (sshesh*) pg-service-name 5432)


  )

(defn execute! []
  (let [sshesh (sshesh*)
        ;; install dokku
        bootstrap-fpath "/tmp/dokku_bootstrap.sh"
        _ (get-url! sshesh
                    {:dest bootstrap-fpath
                     :url "https://dokku.com/install/v0.30.6/bootstrap.sh"})
        _ (when-not (is-dokku-installed? sshesh)
            (let [bootstrap-res
                  (ssh-exec sshesh (str "sudo DOKKU_TAG=v0.30.6 bash " bootstrap-fpath))
                  _ (pp/pprint {:bootstrap-res bootstrap-res})]))
        ;; add admin ssh keys
        _ (sync-dokku-ssh-admin-keys! sshesh)
        _ (set-global-domain! sshesh)
        _ (when-not (is-postgresql-plugin-installed? sshesh)
            (install-postgresql-plugin! sshesh))
        _ (when-not (does-pg-service-exist? sshesh pg-service-name)
            (install-pg-service! sshesh pg-service-name))
        _ (when-not (= "5432->5432" (get-pg-exposed-ports sshesh pg-service-name))
            (println "exposing port")
            (expose-pg-port sshesh pg-service-name 5432))]))

(comment

  (execute!)


  )
