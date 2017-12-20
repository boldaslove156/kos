(ns kos.event
  #?(:clj
     (:require
      [clojure.core.async :as asn]
      [mount.core :refer [defstate]]
      [taoensso.timbre :as tmb])
     :cljs
     (:require
      [cljs.core.async :as asn]
      [mount.core :refer-macros [defstate]]
      [taoensso.timbre :as tmb :include-macros true])))

(defn start-event-dispatcher!
  ([]
   (start-event-dispatcher! (asn/chan 1000)))
  ([ch]
   (tmb/info "Starting event dispatcher...")
   {:dispatch-ch ch}))

(defn dispatch-chan
  [event-dispatcher]
  (:dispatch-ch event-dispatcher))

(defn stop-event-dispatcher!
  [event-dispatcher]
  (tmb/info "Stopping event dispatcher...")
  (asn/close! (dispatch-chan event-dispatcher)))

(defstate event-dispatcher
  :start (start-event-dispatcher!)
  :stop (stop-event-dispatcher! @event-dispatcher))

(defn dispatch!
  [event-dispatcher event]
  (asn/put! (dispatch-chan event-dispatcher) event))
