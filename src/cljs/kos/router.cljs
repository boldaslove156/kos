(ns kos.router
  (:require
   [mount.core :refer-macros [defstate]]
   [bidi.router :as bd.rtr]
   [taoensso.timbre :as tmb :include-macros true]
   [taoensso.encore :refer-macros [do-true]]
   [kos.event :as evt]
   [kos.routes :as rts]))

(defn start-router!
  [routes event-dispatcher]
  (tmb/info "Starting router...")
  (let [on-navigate (fn [{:keys [handler route-params] :as location}]
                      (let [new-location {:location/handler      handler
                                          :location/route-params route-params}
                            event        {:event/id :db-conn/add-locations
                                          :locations [new-location]}]
                        (evt/dispatch! event-dispatcher event)))]
    (do-true
     (bd.rtr/start-router! routes on-navigate))))

(defstate router
  :start (start-router! rts/client-routes @evt/event-dispatcher)
  :stop ::stop)
