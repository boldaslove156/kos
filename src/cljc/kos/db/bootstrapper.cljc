(ns kos.db.bootstrapper
  #?(:clj
     (:require
      [mount.core :refer [defstate]]
      [datascript.core :as dts]
      [datomic-schema.schema :as dtm.sch :refer [schema fields]]
      [io.rkn.conformity :as dtm.cnf]
      [taoensso.timbre :as tmb]
      [taoensso.encore :as enc]
      [kos.db :as db])
     :cljs
     (:require
      [mount.core :refer-macros [defstate]]
      [datascript.core :as dts]
      [taoensso.timbre :as tmb]
      [taoensso.encore :as enc :include-macros true]
      [kos.db :as db])))

#?(:clj
   (defn config->tx-data
     [{:keys [schema data]}]
     (enc/into-all []
                   (dtm.sch/generate-schema schema {:gen-all?   true
                                                    :index-all? true})
                   data)))

#?(:clj
   (defn generate-conformable-map
     [config]
     (enc/map-vals (fn [conformable-configs]
                     (enc/cond!
                      (vector? conformable-configs)
                      {:txes (into []
                                   (map config->tx-data)
                                   conformable-configs)}

                      (symbol? conformable-configs)
                      {:txes-fn conformable-configs}))
                   config)))

#?(:clj
   (defn conformity-config
     []
     (sorted-map
      ::v1 [{:schema [(schema db.entity
                        (fields
                         [id :string :unique-identity]))
                      (schema user
                        (fields
                         [name :string]
                         [emails :string :many :unique-identity]
                         [password :string]
                         [created-at :instant]))
                      (schema role
                        (fields
                         [name :string :unique-identity]
                         [creator :ref]
                         [members :ref :many]
                         [created-at :instant]))]}])))

(defn replace-schema-map!
  [db-conn schema-map]
  (when (= :datascript (db/what-db db-conn))
    (swap! db-conn
           (fn [db-db schema-map]
             (dts/init-db (dts/datoms db-db :eavt) schema-map))
           schema-map)))

(defn merge-schema-map!
  [db-conn next-schema-map]
  (when (= :datascript (db/what-db db-conn))
    (swap! db-conn
           (fn [db-db next-schema-map]
             (let [prev-schema-map (:schema db-db)]
               (dts/init-db (dts/datoms db-db :eavt)
                            (enc/nested-merge-with
                             (fn [m1 m2]
                               (enc/cond
                                 (and (map? m1) (map? m2))
                                 (enc/merge m1 m2)

                                 (and (coll? m1) (coll? m2))
                                 (into m1 m2)

                                 m2))
                             prev-schema-map
                             next-schema-map))))
           next-schema-map)))

(defn bootstrapping-db!
  [db-conn]
  (tmb/info "Bootstrapping database...")
  (case (db/what-db db-conn)
    :datomic    #?(:clj (let [conformable-map (generate-conformable-map
                                               (conformity-config))]
                          (dtm.cnf/ensure-conforms db-conn conformable-map)))
    :datascript (replace-schema-map!
                 db-conn
                 ;; TODO: conform for clj
                 {:db.entity/id   {:db/unique :db.unique/identity}
                  :db/ident       {:db/unique :db.unique/identity}
                  :db/valueType   {:db/valueType :db.type/ref}
                  :db/unique      {:db/valueType :db.type/ref}
                  :db/cardinality {:db/valueType :db.type/ref}})))

(defstate db-bootstrapper
  :start (bootstrapping-db! @db/db-conn)
  :stop ::stop)
