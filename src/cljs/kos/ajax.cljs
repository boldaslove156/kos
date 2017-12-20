(ns kos.ajax
  (:require
   [mount.core :refer-macros [defstate]]
   [taoensso.sente :as snt]
   [taoensso.timbre :as tmb :include-macros true]
   [taoensso.encore :as enc :include-macros true]
   [kos.config :as cfg]
   [kos.event :as evt]
   [kos.routes :as rts]))

(defn ajax-option
  [config option]
  (let [timeout-ms (enc/have (:timeout config))]
    (enc/merge {:timeout-ms timeout-ms} option)))

(defn start-ajax-caller!
  [config event-dispatcher]
  (tmb/info "Starting ajax caller...")
  (fn [target params option callback-event]
    (let [uri (if (string? target)
                target
                (rts/path-for :server target params))]
      (snt/ajax-lite uri
                     (ajax-option config option)
                     (fn [response]
                       (evt/dispatch!
                        event-dispatcher
                        (enc/merge callback-event response)))))))

(defstate ajax-caller
  :start (start-ajax-caller! (enc/have (:ajax @cfg/config))
                             @evt/event-dispatcher)
  :stop ::stop)
