(ns kos.router
  (:require
   [mount.core :refer [defstate]]
   [bidi.ring :as bd.rg]
   [taoensso.timbre :as tmb]
   [kos.router.middleware :as rtr.mdw]
   [kos.resources :as rsc]
   [kos.routes :as rts]))

(def handler->resource
  {:ws/ajax            rsc/ws-resource
   :static-files/asset rsc/asset-resource
   :static-files/index rsc/index-resource
   :auth/login         rsc/login-resource})

(defn start-router!
  [middleware routes route-mapping]
  (tmb/info "Starting router...")
  (middleware (bd.rg/make-handler routes route-mapping)))

(defstate router
  :start (start-router! @rtr.mdw/middleware
                        rts/server-routes
                        handler->resource)
  :stop ::stop)
