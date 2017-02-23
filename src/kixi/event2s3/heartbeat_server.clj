(ns kixi.event2s3.heartbeat-server
  (require [clojure.java.io :as io]
           [bidi.bidi :refer [tag]]
           [bidi.vhosts :refer [make-handler vhosts-model]]
           [com.stuartsierra.component :as component]
           [taoensso.timbre :as timbre :refer [infof]]
           [yada.yada :as yada :refer [resource]]
           [yada.consume :refer [save-to-file]]
           [yada.resources.webjar-resource :refer [new-webjar-resource]]))

(defn bidi-routes []
  ["" [["/healthcheck" (yada/handler "Peers are alive.\n")]]])

(defn routes
  "Create the URI route structure for our application."
  [config]
  [""
   [(bidi-routes)
    ;; This is a backstop. Always produce a 404 if we ge there. This
    ;; ensures we never pass nil back to Aleph.
    [true (yada/handler nil)]]])

(defrecord WebServer [port listener vhost]
  component/Lifecycle
  (start [component]
    (if listener
      component
      (let [vhosts-model
            (vhosts-model
             [{:scheme :http :host (format "%s:%d" vhost port)}
              (routes {:port port})])
            listener (yada/listener vhosts-model {:port port})]
        (infof "Started web-server on port %s")
        (assoc component :listener listener))))
  (stop [component]
    (when-let [close (get-in component [:listener :close])]
      (close))
    (assoc component :listener nil)))

(defn new-web-server [config]
  (map->WebServer config))
