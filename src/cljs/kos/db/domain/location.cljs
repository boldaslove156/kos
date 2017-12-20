(ns kos.db.domain.location
  (:require
   [datascript.core :as dts]
   [taoensso.encore :as enc :include-macros true]
   [kos.db :as db]))

(defn new-tx
  [what location]
  (let [entity-id (or (:db.entity/id location) (db/gen-entity-id what))
        tempid (or (:db/id location) (db/tempid what :db.part/user))]
    (enc/conj-when
     [[:db/add tempid :db.entity/id entity-id]
      [:db/add tempid :location/handler (:location/handler location)]]
     (when-let [route-params (:location/route-params location)]
       [:db/add tempid :location/route-params route-params]))))
