(ns kos.event.listener
  #?(:clj
     (:require
      [clojure.core.async :as asn]
      [mount.core :refer [defstate]]
      [taoensso.timbre :as tmb]
      [taoensso.encore :as enc]
      [kos.impl.listener :as ipl.lst]
      [kos.event :as evt]
      [kos.config :as cfg]
      [kos.ws :as ws]
      [kos.db :as db])
     :cljs
     (:require
      [cljs.core.async :as asn]
      [mount.core :refer-macros [defstate]]
      [taoensso.timbre :as tmb :include-macros true]
      [taoensso.encore :as enc :include-macros true]
      [kos.impl.listener :as ipl.lst]
      [kos.event :as evt]
      [kos.config :as cfg]
      [kos.ws :as ws]
      [kos.db :as db]
      [kos.element :as el]
      [kos.ajax :as jx])))

(defmulti handle-effect!
  (fn [service effect]
    (:effect/id effect)))

(defn act-to
  [effect-id handler]
  (defmethod handle-effect! effect-id
    [service effect]
    (tmb/debug "Handling incoming effect:" effect-id)
    (handler service effect)))

(defn handle-effects!
  [service effects]
  (when (seq effects)
    (->> effects
         (remove nil?)
         (run! (partial handle-effect! service)))))

(defmulti handle-event
  (fn [service event]
    (:event/id event)))

(defn listen-to
  [event-id handler]
  (defmethod handle-event event-id
    [service event]
    (tmb/debug "Handling incoming event:" event-id)
    (handler service event)))

(defn event-handler
  [{:keys [db-conn] :as service} event]
  (let [service (assoc service :db-db (db/db db-conn))]
    (->> event
         (handle-event
          (select-keys service [:db-db :config]))
         (handle-effects! service))))

(defn start-event-listener!
  [service event-dispatcher]
  (tmb/info "Listening on event dispatcher...")
  (let [to-listen-ch (evt/dispatch-chan event-dispatcher)
        service      (assoc service :event-dispatcher event-dispatcher)
        stop-ch      (asn/chan)
        handler      (partial event-handler service)]
    (ipl.lst/go-react! to-listen-ch stop-ch handler)
    {:stop-ch stop-ch}))

(declare stop-chan)

(defn stop-event-listener!
  [event-listener]
  (tmb/info "No longer listening on event dispatcher...")
  (asn/close! (stop-chan event-listener)))

(defn stop-chan
  [event-listener]
  (:stop-ch event-listener))

(defstate event-listener
  :start (start-event-listener! #?(:clj {:ws-server @ws/ws-server
                                         :db-conn   @db/db-conn
                                         :config    @cfg/config}
                                   :cljs {:ws-server   @ws/ws-server
                                          :db-conn     @db/db-conn
                                          :element     @el/element
                                          :config      @cfg/config
                                          :ajax-caller @jx/ajax-caller})
                                @evt/event-dispatcher)
  :stop (stop-event-listener! @event-listener))

;; ======================================================
;; Register handler
;; ======================================================

(listen-to
 :default
 (fn [service event]
   (tmb/warn "Incoming unknown event with id:" (:event/id event))))

(act-to
 :default
 (fn [service effect]
   (tmb/warn "Incoming unknown effect with id:" (:effect/id effect))))

(act-to
 :event-dispatcher/event
 (fn [{:keys [event-dispatcher]} {:keys [event]}]
   (evt/dispatch! event-dispatcher event)))

(listen-to
 :event-dispatcher/effects
 (fn [service {:keys [effects]}]
   effects))
