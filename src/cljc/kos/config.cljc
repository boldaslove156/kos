(ns kos.config
  #?(:clj
     (:require
      [mount.core :as mnt :refer [defstate]]
      [aero.core :as aro]
      [taoensso.timbre :as tmb]
      [taoensso.encore :as enc])
     :cljs
     (:require
      [mount.core :refer-macros [defstate]]
      [taoensso.timbre :as tmb :include-macros true])))

(defn read-config!
  [profile]
  (tmb/info "Reading config...")
  #?(:clj (let [source      "resources/private/edn/kos/config.edn"
                base-config (aro/read-config source {:profile profile})]
            (assoc base-config :profile profile))
     :cljs {:ws {:timeout 5000}
            :ajax {:timeout 5000}
            :db {:what :datascript}}))

(defstate config
  :start (read-config! #?(:clj (enc/have [:el #{:dev}] (:profile (mnt/args)))
                          :cljs nil))
  :stop ::stop)
