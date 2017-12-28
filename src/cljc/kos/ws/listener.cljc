(ns kos.ws.listener
  #?(:clj
     (:require
      [clojure.core.async :as asn]
      [mount.core :refer [defstate]]
      [taoensso.timbre :as tmb]
      [taoensso.encore :as enc]
      [kos.impl.listener :as ipl.lst]
      [kos.event :as evt]
      [kos.event.listener :refer [act-to listen-to]]
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
      [kos.event.listener :refer [listen-to act-to]]
      [kos.ws :as ws]
      [kos.db :as db])))

(defn bootstrap-incoming-event
  [event]
  #?(:clj (let [{event-id     :id
                 event-data   :?data
                 ring-request :ring-req
                 peer-id      :uid
                 device-id    :client-id
                 ?reply-fn    :?reply-fn} event]
            (enc/merge {:event/id event-id}
                       (enc/cond
                         (map? event-data)
                         event-data

                         (some? event-data)
                         {:data event-data})
                       {:ws/ring-request ring-request
                        :ws/peer-id      peer-id
                        :ws/device-id    device-id}
                       (when ?reply-fn
                         {:ws/?reply-fn ?reply-fn})))
     :cljs
     (let [[event-id event-data] (:event event)]
       (enc/merge {:event/id event-id}
                  (enc/cond
                    (map? event-data)
                    event-data

                    (some? event-data)
                    {:ws/data event-data})))))

(defn event-handler
  [event-dispatcher event]
  (->> event
       (bootstrap-incoming-event)
       (evt/dispatch! event-dispatcher)))

(defn start-ws-listener!
  [ws-server event-dispatcher]
  (tmb/info "Starting web socket listener...")
  (let [listen-ch (ws/listen-chan ws-server)
        handler   (partial event-handler event-dispatcher)
        stop-ch   (asn/chan)]
    (ipl.lst/go-react! listen-ch stop-ch handler)
    {:stop-ch stop-ch}))

(declare stop-chan)

(defn stop-ws-listener!
  [ws-listener]
  (tmb/info "Stopping web socket listener...")
  (asn/close! (stop-chan ws-listener)))

(defstate ws-listener
  :start (start-ws-listener! @ws/ws-server @evt/event-dispatcher)
  :stop (stop-ws-listener! @ws-listener))

(defn stop-chan
  [event-listener]
  (:stop-ch event-listener))

;; ======================================================
;; Register handler
;; ======================================================

(defn bootstrap-publish-callback
  [event-dispatcher publish-event]
  (fn [remote-event]
    (let [event (enc/merge publish-event remote-event)]
      (evt/dispatch! event-dispatcher event))))

(act-to
 :ws-server/publish
 #?(:clj (fn [{:keys [ws-server]} {:keys [peer-id event]}]
           (if (some? peer-id)
             (ws/publish! ws-server peer-id event)
             (ws/publish! ws-server event)))
    :cljs (fn [{:keys [ws-server event-dispatcher]} {:keys [event more]}]
            (let [new-more (if-let [publish-event (second more)]
                             (conj [(first more)]
                                   (bootstrap-publish-callback event-dispatcher
                                                               publish-event))
                             more)]
              (apply ws/publish! ws-server event new-more)))))

#?(:clj
   (act-to
    :ws-server/reply
    (fn [service {:keys [reply-fn reply-data]}]
      (reply-fn reply-data))))

(listen-to
 :chsk/ws-ping
 (fn [service event]
   (tmb/debug "PING!")))

#?(:cljs
   (listen-to
    :chsk/state
    (fn [service {:keys [ws/data]}]
      ;; kalo mau tiap buka client: (:open? new-state)
      ;; kalo mau check apa uda di close \\
      ;; (and (not (:open? old-state)) (not (:open? new-state)))
      (let [[old-state new-state] data]
        [(when (:first-open? new-state)
           {:effect/id :event-dispatcher/event
            :event     {:event/id :db-conn/request-bootstrap}})
         {:effect/id :ws-server/publish
          :event     {:event/id :ws-server/myself}}]))))

#?(:cljs
   (act-to
    :ws-server/reconnect
    (fn [{:keys [ws-server]} effect]
      (ws/reconnect! ws-server))))

#?(:clj
   (listen-to
    :ws-server/myself
    (fn [{:keys [db-db]} {:keys [ws/ring-request ws/peer-id] :as event}]
      (enc/when-let [entity-id (get-in ring-request
                                       [:identity :db.entity/id])
                     self-user (db/entity db-db [:db.entity/id entity-id])]
        [{:effect/id :ws-server/publish
          :peer-id   peer-id
          :event     {:event/id  :db-conn/apply-pull-data
                      :pull-data [(-> self-user
                                      (select-keys [:db.entity/id
                                                    :user/name
                                                    :user/emails])
                                      (assoc :user/me? true))]}}]))))
