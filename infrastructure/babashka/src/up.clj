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
            (throw (ex-info "error adding key" {:cmd-res res})))]))

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

(defn execute! []
  (let [sshesh (sshesh*)
        ;; install dokku
        bootstrap-fpath "/tmp/dokku_bootstrap.sh"
        _ (get-url! sshesh
                    {:dest bootstrap-fpath
                     :url "https://dokku.com/install/v0.30.6/bootstrap.sh"})
        bootstrap-res
        (-> (bbssh/exec sshesh
                        (str "sudo DOKKU_TAG=v0.30.6 bash" bootstrap-fpath)
                        {:out :string :err :string})
            deref)
        _ (pp/pprint {:bootstrap-res bootstrap-res})
        ;; add admin ssh keys
        _ (sync-dokku-ssh-admin-keys! sshesh)]))

(comment

  (execute!)


  )
