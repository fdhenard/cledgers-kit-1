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
        (str user-home "/.ssh/id_rsa"))))

(comment

  (private-key-path*)

  (config*)


  )

;; (agent/set-debug-fn
;;  (fn [_level message]
;;    (binding [*out* *err*]
;;      (println message))))

(defn sshesh* []
  (bbssh/ssh (get-in (config*) [:inventory :hostname])
             {:username (get-in (config*) [:inventory :username])
              :identity (private-key-path*)}))

(comment

  ;; in a terminal
  ;; bb nrepl-server 1667
  ;; in emacs
  ;; M-x cider-connect <enter> localhost <enter> 1667 <enter>

  (try
    (-> (sshesh*)
        (bbssh/exec "echo 'I am running remotely'" {:out :string})
        deref
        :out
        )
    (catch clojure.lang.ExceptionInfo ei
      (pp/pprint {:ei-data (ex-data ei)})
      (println (ex-message ei))))



  )

(defn file-exists? [sshesh config]
  (let [res (-> (bbssh/exec sshesh
                            (str "ls " (:dest config))
                            {:out :string
                             :err :string})
                deref)]
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
    (let [res (-> (bbssh/exec sshesh
                              (str "wget -NO " (:dest config) " " (:url config))
                              {:out :string
                               :err :string})
                  deref)
          _ (when-not (= 0 (:exit res))
              (throw (ex-info "unexpected :exit val from exec" {:res res})))])))

(comment

  (get-url! sshesh
            {:dest "/tmp/dokku_bootstrap.sh"
             :url "https://dokku.com/install/v0.30.6/bootstrap.sh"})


  )

(defn is-dokku-installed? [sshesh]
  (let [cmd-res (-> (bbssh/exec sshesh
                                "dokku version"
                                {:out :string :err :string})
                    deref)
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
  (let [cmd-res (-> (bbssh/exec sshesh
                                (str "dokku ssh-keys:list --format json")
                                {:out :string :err :string})
                    deref)]
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
        res (-> (bbssh/exec sshesh
                            (str "echo \"" pubkey "\" | sudo dokku ssh-keys:add " (name key-id))
                            {:out :string :err :string})
                deref)
        #_ (pp/pprint {:res res})
        _ (when (= 1 (:exit res))
            (throw (ex-info "error adding key" {:cmd-res res})))]))

(defn remove-dokku-ssh-admin-key! [sshesh key-id]
  (let [res (-> (bbssh/exec sshesh
                            (str "sudo dokku ssh-keys:remove " (name key-id))
                            {:out :string :err :string})
                deref)
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
  (let [$ (-> (bbssh/exec sshesh
                          (str "dokku domains:report --global")
                          {:out :string :err :string})
              deref)
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
  (let [cmd-res (-> (bbssh/exec sshesh
                                (str "dokku domains:set-global " (get-in (config*) [:inventory :hostname]))
                                {:out :string :err :string})
                    deref)
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

(defn execute! []
  (let [sshesh (sshesh*)
        ;; install dokku
        bootstrap-fpath "/tmp/dokku_bootstrap.sh"
        _ (get-url! sshesh
                    {:dest bootstrap-fpath
                     :url "https://dokku.com/install/v0.30.6/bootstrap.sh"})
        _ (when-not (is-dokku-installed? sshesh)
            (let [bootstrap-res
                  (-> (bbssh/exec sshesh
                                  (str "sudo DOKKU_TAG=v0.30.6 bash " bootstrap-fpath)
                                  {:out :string :err :string})
                      deref)
                  _ (pp/pprint {:bootstrap-res bootstrap-res})]))
        ;; add admin ssh keys
        _ (sync-dokku-ssh-admin-keys! sshesh)
        _ (set-global-domain! sshesh)]))

(comment

  (execute!)


  )
