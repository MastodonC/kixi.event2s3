(def slf4j-version "1.7.21")
(defproject kixi.event2s3 "0.1.1-SNAPSHOT"
  :description ""
  :url ""
  :license {:name ""
            :url ""}
  :dependencies [[aero "1.0.3"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.onyxplatform/onyx "0.9.15"]
                 [org.onyxplatform/lib-onyx "0.9.15.0"]
                 [org.onyxplatform/onyx-kafka "0.9.15.0"]
                 [org.onyxplatform/onyx-amazon-s3 "0.9.15.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [ymilky/franzy-admin "0.0.1" :exclusions [org.slf4j/slf4j-log4j12]]
                 [clj-time "0.10.0"]
                 [com.fasterxml.jackson.core/jackson-core "2.6.6"]
                 [com.cognitect/transit-clj "0.8.297"]
                 [org.clojure/data.csv "0.1.3"]
                 [com.taoensso/timbre "4.8.0"]
                 [environ "1.1.0"]
                 [cheshire "5.6.3"]
                 [org.clojure/tools.cli "0.3.3"]
                 [yada "1.2.0"]
                 [bidi "2.0.9"]
                 [aleph "0.4.1"]
                 [org.slf4j/log4j-over-slf4j ~slf4j-version]
                 [org.slf4j/jul-to-slf4j ~slf4j-version]
                 [org.slf4j/jcl-over-slf4j ~slf4j-version]
                 [kixi/kixi.log "0.1.1"]]
  :source-paths ["src"]

  :profiles {:dev {:jvm-opts ["-XX:-OmitStackTraceInFastThrow"]
                   :global-vars {*assert* true}
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [lein-project-version "0.1.0"]]}

             :uberjar {:aot [lib-onyx.media-driver
                             kixi.event2s3.core]
                       :uberjar-name "peer.jar"
                       :global-vars {*assert* false}}}
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version"
                   "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
