(ns kos.resources
  (:require
   [clojure.java.io :as jio]
   [rum.core :as rum :refer [defc]]
   [ring.util.http-response :as rg.res]
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

    (rg.res/method-not-allowed)))

(defn asset-resource
  [request]
  (enc/cond
    (not= :get (:request-method request))
    (rg.res/method-not-allowed)

    :let [file (jio/file (subs (:uri request) 1))]

    (and (.exists file) (.isFile file))
    (rg.res/ok file)

    (rg.res/not-found)))

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
          (rg.res/ok)
          (rg.res/header "content-type" "text/html")))
    (rg.res/method-not-allowed)))

(defn login-resource
  [request]
  (enc/cond
    (not= :post (:request-method request))
    (rg.res/method-not-allowed)

    (some? (:session request))
    (rg.res/unauthorized)

    :let [body  (:body request)
          db-db (enc/have (get-in request [:services :db-db]))
          user  (db.dmn.usr/find-user-by-credential db-db body)]

    (nil? user)
    (rg.res/forbidden)

    :let [user (select-keys user [:db.entity/id :user/name :user/email])
          user-token (db.dmn.usr/tokenize user)]

    (assoc (rg.res/ok) :session user-token)))
