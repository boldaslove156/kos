(ns kos.router.middleware
  (:require
   [mount.core :refer [defstate]]
   [ring.middleware.defaults :as rg.mdw.def]
   [taoensso.timbre :as tmb]
   [kos.config :as cfg]
   [kos.db :as db]
   [kos.ws :as ws]))

(defn wrap-trailing-slash
  [handler]
  (fn [request]
    (let [uri (:uri request)]
      (handler (assoc request :uri (if (and (not= "/" uri)
                                            (.endsWith uri "/"))
                                     (subs uri 0 (dec (count uri)))
                                     uri))))))

(defn wrap-default
  [handler]
  (rg.mdw.def/wrap-defaults handler rg.mdw.def/site-defaults))

(defn wrap-config
  [handler config]
  (fn [request]
    (handler (assoc-in request [:services :config] config))))

(defn wrap-db
  [handler db-conn]
  (fn [request]
    (let [db-db (db/db db-conn)]
      (handler (-> request
                   (assoc-in [:services :db-conn] db-conn)
                   (assoc-in [:services :db-db] db-db))))))

(defn wrap-ws-server
  [handler ws-server]
  (fn [request]
    (handler (assoc-in request [:services :ws-server] ws-server))))

(defn start-middleware!
  [config db-conn ws-server]
  (tmb/info "Starting middleware...")
  (fn [handler]
    (-> handler
        (wrap-default)
        (wrap-db db-conn)
        (wrap-ws-server ws-server)
        (wrap-config config)
        (wrap-trailing-slash))))

(defstate middleware
  :start (start-middleware! @cfg/config
                            @db/db-conn
                            @ws/ws-server))
