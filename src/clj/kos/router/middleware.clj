(ns kos.router.middleware
  (:require
   [clojure.string :as str]
   [mount.core :refer [defstate]]
   [ring.middleware.defaults :as rg.mdw.def]
   [ring.util.http-response :as rg.tl.res]
   [muuntaja.middleware :as mtj]
   [buddy.auth :as bdy.ath]
   ;; [buddy.auth.http :as bdy.ath.htp]
   [buddy.auth.protocols :as bdy.ath.prt]
   [buddy.auth.middleware :as bdy.ath.mdw]
   [buddy.sign.jwt :as bdy.sgn.jwt]
   [taoensso.timbre :as tmb]
   [taoensso.encore :as enc]
   [kos.config :as cfg]
   [kos.db :as db]
   [kos.ws :as ws]
   [kos.resources :as rsc]))

(defn parse-cookie-token-header
  [request token-name]
  (get-in request [:cookies token-name :value]))

(defn handle-unauthorized-default
  [request]
  (if (bdy.ath/authenticated? request)
    (rg.tl.res/forbidden)
    (rg.tl.res/unauthorized)))

(defn jwe-in-cookies-backend
  [{:keys [token-name secret option on-error unauthorized-handler]}]
  {:pre [(enc/have? secret) (enc/have? string? token-name)]}
  (reify
    bdy.ath.prt/IAuthentication
    (-parse [_ request]
      (parse-cookie-token-header request token-name))
    (-authenticate [_ request data]
      (enc/catching (bdy.sgn.jwt/decrypt data secret option)
                    error
                    (let [error-data (enc/error-data error)]
                      (when (fn? on-error)
                        (on-error error-data)))))
    bdy.ath.prt/IAuthorization
    (-handle-unauthorized [_ request metadata]
      (if (fn? unauthorized-handler)
        (unauthorized-handler request metadata)
        (handle-unauthorized-default request)))))

(defn wrap-auth
  [handler]
  (let [option (assoc rsc/auth-option :token-name "usrtkn")
        backend (jwe-in-cookies-backend option)]
    (bdy.ath.mdw/wrap-authentication handler backend)))

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
        (wrap-auth)
        (mtj/wrap-format)
        (wrap-default)
        (wrap-db db-conn)
        (wrap-ws-server ws-server)
        (wrap-config config)
        (wrap-trailing-slash))))

(defstate middleware
  :start (start-middleware! @cfg/config
                            @db/db-conn
                            @ws/ws-server))
