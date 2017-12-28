(ns kos.db
  #?(:clj
     (:require
      [mount.core :refer [defstate]]
      [datomic.api :as dtm]
      [datascript.core :as dts]
      [taoensso.timbre :as tmb]
      [taoensso.encore :as enc]
      [kos.config :as cfg])
     :cljs
     (:require
      [goog.crypt.base64 :as base64]
      [mount.core :refer-macros [defstate]]
      [datascript.core :as dts]
      [taoensso.timbre :as tmb :include-macros true]
      [taoensso.encore :as enc :include-macros true]
      [kos.config :as cfg]))
  #?(:clj
     (:import
      [java.util Base64])))

(defn connect-to-db!
  [config]
  (tmb/info "Connecting to database...")
  (let [what (enc/have (:what config))]
    (case what
      :datomic    #?(:clj (let [uri      (enc/have (:uri config))
                                created? (dtm/create-database uri)
                                conn     (dtm/connect uri)]
                            conn)
                     :cljs (throw (ex-info "No Datomic for Clojurescript!"
                                           {:what what})))
      :datascript (dts/create-conn))))

(defn what-db
  [x]
  (cond
    (or (dts/db? x)
        (dts/conn? x))
    :datascript

    #?@(:clj [(or (instance? datomic.Connection x)
                  (instance? datomic.Database x))
              :datomic])))

(defn disconnect-from-db!
  [db-conn]
  (tmb/info "Disconnecting from database...")
  (let [what (what-db db-conn)]
    (case what
      :datomic    #?(:clj (dtm/release db-conn)
                     :cljs (throw (ex-info "No Datomic for Clojurescript!"
                                           {:what what})))
      :datascript ::stop)))

(defstate db-conn
  :start (connect-to-db! (enc/have map? (:db @cfg/config)))
  :stop (disconnect-from-db! @db-conn))

;; ================================================================
;; API
;; ================================================================

(defn tempid
  ([what part]
   (case what
     :datomic    #?(:clj (dtm/tempid part))
     :datascript (dts/tempid part)))
  ([what part n]
   (case what
     :datomic    #?(:clj (dtm/tempid part n))
     :datascript (dts/tempid part n))))

(defn encode-base64
  [input]
  #?(:clj (.encodeToString (Base64/getEncoder) (.getBytes input))
     :cljs (base64/encodeString input)))

(defn gen-entity-id
  [what]
  (let [uuid (case what
               :datomic    #?(:clj (dtm/squuid))
               :datascript (dts/squuid))]
    (encode-base64 (str uuid))))

(defn db
  [db-conn]
  (case (what-db db-conn)
    :datomic    #?(:clj (dtm/db db-conn))
    :datascript (dts/db db-conn)))

(defn entid
  [db-db ident]
  (case (what-db db-db)
    :datomic    #?(:clj (dtm/entid db-db ident))
    :datascript (dts/entid db-db ident)))

(defn entity
  [db-db eid]
  (case (what-db db-db)
    :datomic    #?(:clj (when-let [eid (entid db-db eid)]
                          (dtm/entity db-db eid)))
    :datascript (dts/entity db-db eid)))

(defn q
  [query db-db & args]
  (case (what-db db-db)
    :datomic    #?(:clj (apply dtm/q query db-db args))
    :datascript (apply dts/q query db-db args)))

(defn qentity
  [query db-db & args]
  (let [result (enc/have [:or empty? enc/singleton?]
                         (apply q query db-db args))
        eid    (enc/have [:or nil? enc/pos-int?]
                         (ffirst result))]
    (when (some? eid)
      (entity db-db eid))))

(defn datoms
  [db-db index & components]
  (case (what-db db-db)
    :datomic    #?(:clj (apply dtm/datoms db-db index components))
    :datascript (apply dts/datoms db-db index components)))

(defn pull-many
  [db-db pattern eids]
  (case (what-db db-db)
    :datomic    #?(:clj (dtm/pull-many db-db pattern eids))
    :datascript (dts/pull-many db-db pattern eids)))

;; *******************************
;; Transaction
;; *******************************

(defn -ensure-non-overridable-id
  [db-db tx-data]
  (let [pairs      (into []
                         (keep (fn [[db-fn eid attr value]]
                                 (when (not= :db.entity/id attr)
                                   [attr value])))
                         tx-data)
        lookup-ref (q '{:find  [[?attr ?value]]
                        :in    [$ [[?attr ?value]]]
                        :where [[?attr-eid :db/ident ?attr]
                                [?attr-eid :db/unique ?uniqueness-eid]
                                [?entity-eid ?attr ?value]]}
                      db-db
                      pairs)]
    (if (nil? lookup-ref)
      tx-data
      (into []
            (keep (fn [[db-fn eid attr value]]
                    (when (not= :db.entity/id attr)
                      [db-fn lookup-ref attr value])))
            tx-data))))

(defn ensure-non-overridable-id
  [db-db tx-data]
  (->> tx-data
       (group-by second)
       (enc/map-vals (partial -ensure-non-overridable-id db-db))
       (reduce-kv (fn [container eid tx-data]
                    (into container tx-data))
                  [])))

(defmulti update-data
  (fn [db-db [db-fn eid attr value]]
    [db-fn attr]))

(defmethod update-data :default
  [db-db data]
  data)

(defn transact
  ([db-conn tx-data tx-meta]
   {:pre [(enc/have? (partial enc/revery? vector?) tx-data)]}
   (let [db-db   (db db-conn)
         tx-data (->> tx-data
                      (ensure-non-overridable-id db-db)
                      (into [] (map (partial update-data db-db))))]
     (case (what-db db-conn)
       :datomic    #?(:clj (let [tx-report @(dtm/transact db-conn tx-data)]
                             (assoc tx-report :tx-meta tx-meta)))
       :datascript @(dts/transact db-conn tx-data tx-meta))))
  ([db-conn tx-data]
   (transact db-conn tx-data {})))
