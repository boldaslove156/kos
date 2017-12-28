(ns kos.http-requester
  #?(:clj
     (:require
      [mount.core :refer [defstate]]
      [ajax.core :as jx]
      [taoensso.timbre :as tmb]
      [taoensso.encore :as enc]
      [kos.routes :as rts]
      [kos.config :as cfg])
     :cljs
     (:require
      [mount.core :refer-macros [defstate]]
      [ajax.core :as jx]
      [taoensso.timbre :as tmb :include-macros true]
      [taoensso.encore :as enc :include-macros true]
      [kos.routes :as rts]
      [kos.config :as cfg])))

(defn fetch!
  [routes route params option]
  (let [uri (rts/path-for routes route params)]
    (jx/GET uri option)))

(defn send!
  [routes route params request-method option]
  (let [uri     (rts/path-for routes route params)
        call-fn (case request-method
                  :post   jx/POST
                  :put    jx/PUT
                  :patch  jx/PATCH
                  :delete jx/DELETE)]
    (call-fn uri option)))

(defn bootstrap-response
  [response]
  {:response/data response})

(defn http-requester-option
  [config option]
  (let [timeout-ms      (get-in config [:ajax :timeout-ms])
        success-handler (enc/have (:handler option))
        error-handler   (:error-handler option success-handler)]
    (enc/merge
     {:timeout timeout-ms}
     option
     {:handler       #(-> %
                          (bootstrap-response)
                          (assoc :response/success? true)
                          (success-handler))
      :error-handler #(-> %
                          (bootstrap-response)
                          (assoc :response/success? false)
                          (error-handler))})))

(defn start-http-requester!
  [config]
  (tmb/info "Starting http requester...")
  (fn [routes route params request-method option]
    (let [option (http-requester-option config option)]
      (if (= :get request-method)
        (fetch! routes route params option)
        (send! routes route params request-method option)))))

(defstate http-requester
  :start (start-http-requester! @cfg/config))
