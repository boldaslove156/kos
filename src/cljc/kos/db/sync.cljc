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

#?(:clj
   (defn attr-missing?
     [db-db attr eid]
     (let [entity (case (db/what-db db-db)
                    :datomic    (dtm/entity db-db eid)
                    :datascript (dts/entity db-db eid))]
       (not (boolean (attr entity))))))

#?(:clj
   (defn bootstrap-data
     [db-db]
     (->> (db/datoms db-db :eavt)
          (into [] (comp (map (fn [[e a v t added?]] e))
                      (enc/xdistinct)))
          (db/pull-many db-db '[*])
          (into [] (filter (comp not :db/fn))))))

;; ================================================================
;; Accept
;; ================================================================

(defn identity-data?
  [[db-fn [unique-attr unique-value] attr value]]
  (= unique-attr attr))

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

#?(:cljs
   (declare new-datoms))

#?(:cljs
   (defn map->datoms
     [m]
     (let [eid (enc/have (:db/id m))]
       (into []
             (mapcat (fn [[attr value]]
                       (new-datoms eid attr value nil true)))
             (dissoc m :db/id)))))

#?(:cljs
   (defn new-datoms
     [eid attr value date added?]
     (enc/cond
       (map? value)
       (conj (map->datoms value)
             [eid attr (:db/id value) date added?])

       (coll? value)
       (into []
             (mapcat #(new-datoms eid attr % date added?))
             value)

       [[eid attr value date added?]])))

#?(:cljs
   (defn list->datoms
     [[eid attr value date added?]]
     (new-datoms eid attr value date added?)))

#?(:cljs
   (defn datomize-data
     [x]
     (enc/cond
       (map? x)
       (map->datoms x)

       (coll? x)
       (list->datoms x))))

#?(:cljs
   (defn datomize
     [data]
     (into [] (mapcat datomize-data) data)))
