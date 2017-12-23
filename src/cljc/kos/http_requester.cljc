(ns kos.http-requester
  #?(:clj
     (:require
      [mount.core :refer [defstate]]
      [ajax.core :as jx]
      [taoensso.timbre :as tmb]
      [taoensso.encore :as enc]
      [kos.routes :as rts]
      [kos.config :as cfg]
      [kos.event :as evt])
     :cljs
     (:require
      [mount.core :refer-macros [defstate]]
      [ajax.core :as jx]
      [taoensso.timbre :as tmb :include-macros true]
      [taoensso.encore :as enc :include-macros true]
      [kos.routes :as rts]
      [kos.config :as cfg]
      [kos.event :as evt])))

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

(defn response-handler
  [event-dispatcher event response]
  (evt/dispatch! event-dispatcher (assoc event :response/data response)))

(defn http-requester-option
  [config event-dispatcher option]
  (let [timeout-ms    (get-in config [:ajax :timeout-ms])
        success-event (enc/have (:success-event option))
        error-event   (:error-event option success-event)]
    (enc/merge
     {:timeout timeout-ms}
     option
     {:handler       (partial response-handler
                        event-dispatcher
                        (assoc success-event :response/success? true))
      :error-handler (partial response-handler
                        event-dispatcher
                        (assoc error-event :response/success? false))})))

(defn start-http-requester!
  [config event-dispatcher]
  (tmb/info "Starting http requester...")
  (fn [routes route params request-method option]
    (let [timeout-ms (get-in config [:ajax :timeout-ms])
          option     (http-requester-option config event-dispatcher option)]
      (if (= :get request-method)
        (fetch! routes route params option)
        (send! routes route params request-method option)))))

(defstate http-requester
  :start (start-http-requester! @cfg/config @evt/event-dispatcher))
