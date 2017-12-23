(ns kos.db.listener
  #?(:clj
     (:require
      [mount.core :refer [defstate]]
      [datomic.api :as dtm]
      [datascript.core :as dts]
      [taoensso.timbre :as tmb]
      [taoensso.encore :as enc]
      [kos.db :as db]
      [kos.db.bootstrapper :as db.bts]
      [kos.db.sync :as db.snc]
      [kos.event :as evt]
      [kos.event.listener :refer [act-to listen-to]])
     :cljs
     (:require
      [mount.core :refer-macros [defstate]]
      [datascript.core :as dts]
      [taoensso.timbre :as tmb :include-macros true]
      [taoensso.encore :as enc :include-macros true]
      [kos.db :as db]
      [kos.db.bootstrapper :as db.bts]
      [kos.db.sync :as db.snc]
      [kos.db.domain.location :as db.dmn.lct]
      [kos.db.domain.user :as db.dmn.usr]
      [kos.event :as evt]
      [kos.event.listener :refer [act-to listen-to]])))

(defn listen-to-db!
  [db-conn db-bootstrapper event-dispatcher]
  (tmb/info "Listening on database...")
  (case (db/what-db db-conn)
    :datomic    #?(:clj
                   (enc/do-true
                    (let [tx-report-queue (dtm/tx-report-queue db-conn)]
                      (future
                        (loop []
                          (let [event {:event/id  :db-conn/tx-report-change
                                       :tx-report (.take tx-report-queue)}]
                            (evt/dispatch! event-dispatcher event)
                            (recur)))))))
    :datascript (enc/do-true
                 (dts/listen!
                  db-conn
                  (fn [tx-report]
                    (let [event {:event/id  :db-conn/tx-report-change
                                 :tx-report tx-report}]
                      (evt/dispatch! event-dispatcher event)))))))

(defn unlisten-db!
  [db-conn]
  (tmb/info "No longer listening on database...")
  (case (db/what-db db-conn)
    :datomic    #?(:clj (enc/do-false
                         (dtm/remove-tx-report-queue db-conn)))
    :datascript ::stop))

(defstate db-listener
  :start (listen-to-db! @db/db-conn
                        @db.bts/db-bootstrapper
                        @evt/event-dispatcher)
  :stop (unlisten-db! @db/db-conn ))


;; ======================================================
;; Register handler
;; ======================================================

(act-to
 :db-conn/transact
 (fn [{:keys [db-conn]} {:keys [tx-data tx-meta]}]
   (let [tx-report (enc/catching (db/transact db-conn tx-data tx-meta)
                                 error
                                 error)]
     (when (enc/error? tx-report)
       (tmb/warn (enc/error-data tx-report))))))

#?(:cljs
   (defn sync?
     [{:keys [tx-meta] :as tx-report}]
     (:db/sync? tx-meta true)))

(listen-to
 :db-conn/tx-report-change
 (fn [{:keys [db-db]} {:keys [tx-report]}]
   [(when #?(:clj true
             :cljs (sync? tx-report))
      {:effect/id :event-dispatcher/event
       :event     {:event/id  :db-conn/sync-tx-report
                   :tx-report tx-report}})
    #?(:cljs {:effect/id :event-dispatcher/event
              :event     {:event/id  :db-conn/check-schema
                          :tx-report tx-report}})
    #?(:cljs {:effect/id :element/mount
              :db-db     db-db})]))

#?(:cljs
   (defn schema-changing?
     [db eid]
     (boolean (:db/ident (db/entity db eid)))))

#?(:cljs
   (def ref-attrs
     [:db/valueType :db/cardinality :db/unique]))

#?(:cljs
   (def schema-pattern
     ['*
      (into {}
            (map (fn [ref-attr]
                   [ref-attr ['*]]))
            ref-attrs)]))

#?(:cljs
   (defn new-schema-map
     [{attr :db/ident :as schema}]
     (let [schema (reduce (fn [schema ref-attr]
                            (update schema ref-attr :db/ident))
                          (dissoc schema :db/ident)
                          ref-attrs)]
       {attr (update schema :db/valueType (fn [val-type]
                                            (when (= :db.type/ref val-type)
                                              val-type)))})))

#?(:cljs
   (listen-to
    :db-conn/check-schema
    (fn [service {:keys [tx-report]}]
      (let [db-after (:db-after tx-report)
            tx-data  (:tx-data tx-report)
            eids     (into []
                           (comp (map first)
                              (filter (partial schema-changing? db-after)))
                           tx-data)]
        (when (seq eids)
          (let [schema-map (->> eids
                                (db/pull-many db-after schema-pattern)
                                (transduce (map new-schema-map) enc/merge))]
            [{:effect/id  :db-conn/merge-schema-map
              :schema-map schema-map}]))))))

#?(:cljs
   (act-to
    :db-conn/merge-schema-map
    (fn [{:keys [db-conn]} {:keys [schema-map]}]
      (db.bts/merge-schema-map! db-conn schema-map))))

(listen-to
 :db-conn/apply-tx-data
 (fn [{:keys [db-db]} {:keys [tx-data]}]
   [{:effect/id :db-conn/transact
     :tx-data   (db.snc/local-tx db-db tx-data)
     :tx-meta   {:db/sync? false}}]))

#?(:cljs
   (listen-to
    :db-conn/apply-pull-data
    (fn [service {:keys [pull-data]}]
      [{:effect/id :db-conn/transact
        :tx-data   (into []
                         (map db.snc/datom->data)
                         (db.snc/datomize pull-data))
        :tx-meta   {:db/sync? false}}])))

(listen-to
 :db-conn/sync-tx-report
 (fn [service {:keys [tx-report]}]
   [{:effect/id :ws-server/publish
     :event     {:event/id :db-conn/apply-tx-data
                 :tx-data  (db.snc/delta-tx tx-report)}}]))

#?(:clj
   (listen-to
    :db-conn/send-bootstrap
    (fn [{:keys [db-db]} {:keys [ws/?reply-fn]}]
      [{:effect/id  :ws-server/reply
        :reply-fn   ?reply-fn
        :reply-data {:pull-data (db.snc/bootstrap-data db-db)}}])))

#?(:cljs
   (listen-to
    :db-conn/request-bootstrap
    (fn [{:keys [config]} event]
      [{:effect/id :ws-server/publish
        :event     {:event/id :db-conn/send-bootstrap}
        :more      [(get-in config [:ws :timeout-ms])
                    {:event/id :db-conn/apply-pull-data}]}])))

#?(:cljs
   (listen-to
    :db-conn/add-locations
    (fn [{:keys [db-conn]} {:keys [locations]}]
      [{:effect/id :db-conn/transact
        :tx-data   (into []
                         (mapcat (partial db.dmn.lct/new-tx :datascript))
                         locations)}])))

#?(:cljs
   (listen-to
    :db-conn/add-users
    (fn [{:keys [db-conn]} {:keys [users]}]
      [{:effect/id :db-conn/transact
        :tx-data   (into []
                         (mapcat (partial db.dmn.usr/new-tx :datascript))
                         users)}])))
