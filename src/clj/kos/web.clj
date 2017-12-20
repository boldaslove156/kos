(ns kos.web
  (:require
   [mount.core :refer [defstate]]
   [aleph.http :as alph.htp]
   [taoensso.timbre :as tmb]
   [taoensso.encore :as enc :refer [have have?]]
   [kos.config :as cfg]
   [kos.router :as rtr]))

(defn start-web-server!
  [router {:keys [port] :as config}]
  {:pre [(have? enc/pos-int? port)]}
  (tmb/info "Starting web server...")
  (alph.htp/start-server router config))

(defn stop-web-server!
  [web-server]
  (tmb/info "Stopping web server...")
  (.close web-server))

(defstate web-server
  :start (start-web-server! @rtr/router
                            (have map? (:web @cfg/config)))
  :stop (stop-web-server! @web-server))
