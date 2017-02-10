(ns kixi.event2s3.core
  (:gen-class)
  (:require [aero.core :refer [read-config]]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]]
            [lib-onyx.peer :as peer]
            [onyx.job]
            [onyx.api]
            [onyx.test-helper]
            [taoensso.timbre :as timbre]
            [com.stuartsierra.component :as component]
            [kixi.event2s3.heartbeat-server :as heartbeat]
            ;; Load plugin classes on peer start
            [onyx.plugin [core-async]]
            ;; Load our jobs
            [kixi.event2s3.jobs [basic]]))

(defn file-exists?
  "Check both the file system and the resources/ directory
  on the classpath for the existence of a file"
  [file]
  (let [f (clojure.string/trim file)
        classf (io/resource file)
        relf (when (.exists (io/as-file f)) (io/as-file f))]
    (or classf relf)))

(def web-server-config
  )

(defn cli-options []
  [["-c" "--config FILE" "Aero/EDN config file"
    :default (io/resource "config.edn")
    :default-desc "resources/config.edn"
    :parse-fn file-exists?
    :validate [identity "File does not exist relative to the workdir or on the classpath"
               read-config "Not a valid Aero or EDN file"]]

   ["-p" "--profile PROFILE" "Aero profile"
    :parse-fn (fn [profile] (keyword (clojure.string/trim profile)))]

   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["Onyx Peer and Job Launcher"
        ""
        "Usage: [options] action [arg]"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  start-peers [npeers]    Start Onyx peers."
        "  submit-job  [job-name]  Submit a registered job to an Onyx cluster."
        ""]
       (clojure.string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

;; not related to the lib-onyx.peer api.
(defn start-peer-internal [n peer-config env-config config]
  (let [n-peers (or (try (Integer/parseInt 1) (catch Exception e)) n)
        _ (timbre/info "Starting peer-group")
        peer-group (onyx.api/start-peer-group peer-config)
        _ (timbre/info "Starting env")
        env (onyx.api/start-env env-config)
        _ (timbre/info "Starting peers")
        peers (onyx.api/start-peers n-peers peer-group)]
    (timbre/infof "Attempting to connect to Zookeeper %s" (:zookeeper/address peer-config))
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread.
                       (fn []
                         (doseq [v-peer peers]
                           (onyx.api/shutdown-peer v-peer))
                         (onyx.api/shutdown-peer-group peer-group)
                         (shutdown-agents))))
    (timbre/info "Started peers. Blocking forever.")
    ;; submit the jobs.
    (onyx.api/submit-job peer-config
                         (onyx.job/register-job "event2s3-job" config))
    (.join (Thread/currentThread))))

(defn assert-job-exists [job-name]
  (let [jobs (methods onyx.job/register-job)]
    (when-not (contains? jobs job-name)
      (exit 1 (error-msg (into [(str "There is no job registered under the name " job-name "\n")
                                "Available jobs: "] (keys jobs)))))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary] :as pargs} (parse-opts args (cli-options))
        action (first args)
        argument (clojure.edn/read-string (second args))]
    (cond (:help options) (exit 0 (usage summary))
          (not= (count arguments) 2) (exit 1 (usage summary))
          errors (exit 1 (error-msg errors)))
    (timbre/info "Arguments:" args)
    (timbre/info "Options:" pargs)
    (case action
      "start-peers" (let [{:keys [env-config peer-config web-server] :as config}
                          (read-config (:config options) {:profile (:profile options)})]
                      (timbre/info config)
                      (.start
                       (heartbeat/new-web-server web-server))
                      (start-peer-internal argument peer-config env-config config)))))
