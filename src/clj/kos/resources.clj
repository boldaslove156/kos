(ns kos.resources
  (:require
   [clojure.java.io :as jio]
   [rum.core :as rum :refer [defc]]
   [ring.util.http-response :as rg.tl.res]
   [buddy.core.nonce :as bdy.nnce]
   [buddy.sign.jwt :as bdy.sgn.jwt]
   [taoensso.encore :as enc]
   [kos.db.domain.user :as db.dmn.usr]
   [kos.db :as db]
   [kos.ws :as ws]))

(defn ws-resource
  [request]
  (enc/cond
    :let [request-method (:request-method request)
          ws-server (enc/have (get-in request [:services :ws-server]))]

    (= :get request-method)
    ((ws/ring-get ws-server) request)

    (= :post request-method)
    ((ws/ring-post ws-server) request)

    (rg.tl.res/method-not-allowed)))

(defn asset-resource
  [request]
  (enc/cond
    (not= :get (:request-method request))
    (rg.tl.res/method-not-allowed)

    :let [file (jio/file (subs (:uri request) 1))]

    (and (.exists file) (.isFile file))
    (rg.tl.res/ok file)

    (rg.tl.res/not-found)))

(defn asset
  [path]
  (str "resources/public/" path))

(defc index-page
  [config]
  [:html
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name    "viewport"
            :content "width=device-width, initial-scale=1, shrink-to-fit=no"}]
    [:title "kos"]]
   [:body
    [:#app]
    [:script {:type "text/javascript"
              :src  (asset "js/kos/app.js")}]]])

(defn index-resource
  [request]
  (if (= :get (:request-method request))
    (let [config (enc/have (get-in request [:services :config]))]
      (-> (rum/render-html (index-page config))
          (rg.tl.res/ok)
          (rg.tl.res/header "content-type" "text/html")))
    (rg.tl.res/method-not-allowed)))

(defonce auth-option
  {:secret (bdy.nnce/random-bytes 32)
   :option {:alg :a256kw :enc :a128gcm}})

(defn tokenize
  [user]
  (let [{:keys [secret option]} auth-option]
    (bdy.sgn.jwt/encrypt user secret option)))

(defn get-cookie
  [req-or-res cookie-name]
  (get-in req-or-res [:cookies cookie-name]))

(defn add-cookie
  [response cookie-name cookie-option]
  {:pre [(enc/have? string? cookie-name)]}
  (assoc-in response [:cookies cookie-name] cookie-option))

(defn login-resource
  [request]
  (enc/cond
    (not= :post (:request-method request))
    (rg.tl.res/method-not-allowed)

    (some? (get-cookie request "usrtkn"))
    (rg.tl.res/unauthorized)

    :let [body  (:body-params request)
          db-db (enc/have (get-in request [:services :db-db]))
          user  (db.dmn.usr/find-user-by-credential db-db body)]

    (nil? user)
    (rg.tl.res/forbidden)

    :let [user-token (-> user
                         (select-keys [:db.entity/id])
                         (tokenize))]

    (add-cookie (rg.tl.res/ok) "usrtkn" {:value     user-token
                                         :http-only true})))

(defn expire-cookie
  [response cookie-name]
  {:pre [(enc/have? string? cookie-name)]}
  (assoc-in response [:cookies cookie-name] {:value   ""
                                             :max-age 0}))

(defn logout-resource
  [request]
  (enc/cond
    (not= :post (:request-method request))
    (rg.tl.res/method-not-allowed)

    (nil? (get-cookie request "usrtkn"))
    (rg.tl.res/unauthorized)

    (expire-cookie (rg.tl.res/ok) "usrtkn")))
