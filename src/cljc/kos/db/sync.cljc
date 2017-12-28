(ns kos.db.sync
  #?(:clj
     (:require
      [datomic.api :as dtm]
      [datascript.core :as dts]
      [taoensso.encore :as enc]
      [kos.db :as db])
     :cljs
     (:require
      [datascript.core :as dts]
      [taoensso.encore :as enc :include-macros true]
      [kos.db :as db])))

(defn ref?
  [db-db attr]
  (let [attr-eid (if (keyword? attr)
                   [:db/ident attr]
                   attr)]
    (boolean (db/q '{:find  [?attr-eid .]
                     :in    [$ ?attr-eid]
                     :where [[?attr-eid :db/valueType ?val-type-eid]
                             [?val-type-eid :db/ident :db.type/ref]]}
                   db-db
                   attr-eid))))

;; ================================================================
;; Send
;; ================================================================

(defn translate-raw-attr
  [db-db [eid attr-eid value tx-eid added? :as data]]
  (case (db/what-db db-db)
    :datomic    #?(:clj [eid
                         (:db/ident (db/entity db-db attr-eid))
                         value
                         tx-eid
                         added?])
    :datascript data))

(defn datom->data
  [[eid attr value _tx-eid added?]]
  (let [db-fn {true  :db/add
               false :db/retract}]
    [(db-fn added?) eid attr value]))

(defn translate-local-eid
  ([db-db eid]
   (enc/cond
     :let [entity (db/entity db-db eid)
           attr   (:db/ident entity)]

     (some? attr)
     [:db/ident attr]

     :let [id (:db.entity/id entity)]

     (some? id)
     [:db.entity/id id]))
  ([db-db attr maybe-eid]
   (if (ref? db-db attr)
     (translate-local-eid db-db maybe-eid)
     maybe-eid)))

(defn translate-local-data
  [db-db-before db-db-after [db-fn eid attr maybe-eid]]
  (let [db-db (if (= :db/add db-fn)
                db-db-after
                db-db-before)]
    [db-fn
     (translate-local-eid db-db eid)
     attr
     (translate-local-eid db-db attr maybe-eid)]))

(defn delta-tx
  [{:keys [db-before db-after tx-data] :as tx-report}]
  (into []
        (comp (map (partial translate-raw-attr db-after))
           (map datom->data)
           (map (partial translate-local-data db-before db-after))
           (remove (comp nil? second)))
        tx-data))

(defn at-least-has-keys?
  [ks m]
  (boolean (some (set ks) (keys m))))

(def ref-attrs
  [:db/valueType :db/cardinality :db/unique])

(def schema-pattern
  ['*
   (into {}
         (map (fn [ref-attr]
                [ref-attr ['*]]))
         ref-attrs)])

(defn bootstrap-data
  [db-db]
  (->> (db/datoms db-db :eavt)
       (into [] (comp (map (fn [[e a v t added?]] e))
                   (enc/xdistinct)))
       (db/pull-many db-db ['*])
       (into [] (remove (partial at-least-has-keys? #{:db/fn})))))

;; ================================================================
;; Accept
;; ================================================================

(defn identity-data?
  [[db-fn eid attr value]]
  (if (enc/vec2? eid)
    (= (first eid) attr)
    false))

(defn translate-remote-eid!
  ([eid-mapping_ db-db eid]
   (let [translated-eid (or (get @eid-mapping_ eid)
                            (db/entid db-db eid)
                            (db/tempid (db/what-db db-db) :db.part/user))]
     (vswap! eid-mapping_ enc/assoc-nx eid translated-eid)
     translated-eid))
  ([eid-mapping_ db-db attr maybe-eid]
   (if (ref? db-db attr)
     (translate-remote-eid! eid-mapping_ db-db maybe-eid)
     maybe-eid)))

(defn translate-remote-data!
  [eid-mapping_ db-db [db-fn eid attr maybe-eid :as data]]
  (case (db/what-db db-db)
    :datomic    #?(:clj [db-fn
                         (translate-remote-eid! eid-mapping_ db-db eid)
                         attr
                         (translate-remote-eid! eid-mapping_
                                                db-db
                                                attr
                                                maybe-eid)])
    :datascript (if (identity-data? data)
                  [db-fn
                   (translate-remote-eid! eid-mapping_ db-db eid)
                   attr
                   (translate-remote-eid! eid-mapping_
                                          db-db
                                          attr
                                          maybe-eid)]
                  data)))

(defn translate-remote-tx-data
  [db-db]
  (fn [xf]
    (let [eid-mapping_ (volatile! {})]
      (fn
        ([]
         (xf))
        ([container]
         (xf container))
        ([container data]
         (xf container (translate-remote-data! eid-mapping_ db-db data)))))))

(defn sort-by-new-data
  [db-db tx-data]
  (case (db/what-db db-db)
    :datomic    #?(:clj tx-data)
    :datascript (let [grouped (group-by (comp enc/neg-int? second) tx-data)]
                  (enc/into-all []
                                (get grouped true)
                                (get grouped false)))))

(defn local-tx
  [db-db tx-data]
  (->> tx-data
       (into [] (translate-remote-tx-data db-db))
       (sort-by-new-data db-db)))


;; ============================================================================
;; Datomize pull result
;; ============================================================================

(declare new-datoms)

(defn get-identifier
  [bootstrap? m]
  (enc/have (or (when bootstrap? (:db/id m))
                (find m :db.entity/id)
                (find m :db/ident))))

(defn map->datoms
  [bootstrap? m]
  (let [eid (get-identifier bootstrap? m)]
    (into []
          (mapcat (fn [[attr value]]
                    (new-datoms bootstrap? eid attr value nil true)))
          (dissoc m :db/id))))

(defn new-datoms
  [bootstrap? eid attr value date added?]
  (enc/cond
    (map? value)
    (conj (map->datoms bootstrap? value)
          [eid attr (get-identifier bootstrap? value) date added?])

    (coll? value)
    (into []
          (mapcat #(new-datoms bootstrap? eid attr % date added?))
          value)

    [[eid attr value date added?]]))

(defn list->datoms
  [bootstrap? [eid attr value date added? :as data]]
  (new-datoms bootstrap? eid attr value date added?))

(defn datomize-data
  [bootstrap? x]
  (enc/cond
    (map? x)
    (map->datoms bootstrap? x)

    (coll? x)
    (list->datoms bootstrap? x)))

(defn datomize
  [bootstrap? pull-data]
  (into [] (mapcat (partial datomize-data bootstrap?)) pull-data))
