(ns kos.db.domain.user
  #?(:clj
     (:require
      [datomic.api :as dtm]
      [datascript.core :as dts]
      [buddy.hashers :as bdy.hsh]
      [taoensso.encore :as enc]
      [kos.db :as db :refer [update-data]])
     :cljs
     (:require
      [datascript.core :as dts]
      [kos.db :as db]
      [taoensso.encore :as enc :include-macros true])))

;; ======================================================
;; Authentication
;; ======================================================

#?(:clj
   (defn- password-match?
     [attempt derived]
     (enc/catching (bdy.hsh/check attempt derived)
                   _
                   false)))

#?(:clj
   (defn find-user-by-credential
     [db-db {:keys [user/email user/password] :as creds}]
     {:pre [(enc/have? some? email password)]}
     (enc/when-let [user (db/entity db-db [:user/email email])
                    derived-password (:user/password user)
                    password-ok? (password-match? password derived-password)]
       user)))

;; ======================================================
;; Data processing
;; ======================================================

#?(:clj
   (defmethod update-data [:db/add :user/password]
     [db [db-fn eid attr value]]
     [db-fn eid attr (bdy.hsh/derive value)]))

;; ======================================================
;; Tx data
;; ======================================================

(defn have-new-user
  [user]
  user)

(defn new-tx
  [what user]
  (let [user (have-new-user user)
        entity-id (or (:db.entity/id user) (db/gen-entity-id what))
        tempid (or (:db/id user) (db/tempid what :db.part/user))]
    [[:db/add tempid :db.entity/id entity-id]
     [:db/add tempid :user/name (:user/name user)]
     [:db/add tempid :user/email (:user/email user)]
     [:db/add tempid :user/password (:user/password user)]]))
