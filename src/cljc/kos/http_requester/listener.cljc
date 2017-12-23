(ns kos.http-requester.listener
  #?(:clj
     (:require
      [taoensso.encore :as enc]
      [kos.event.listener :refer [act-to listen-to]]
      [kos.routes :as rts])
     :cljs
     (:require
      [taoensso.encore :as enc :include-macros true]
      [kos.event.listener :refer [act-to listen-to]]
      [kos.routes :as rts]
      [kos.ws :as ws])))

(defn- invoke-http-requester
  [{:keys [http-req]}
   {:keys [routes route params request-method option]
    :or   {request-method :get
           params         {}}}]
  (http-req routes route params request-method option))

#?(:cljs
   (act-to
    :http-requester/call-server
    (fn [{:keys [ws-server] :as service} effect]
      (let [csrf-token (ws/csrf-token ws-server)
            effect (-> effect
                       (assoc :routes rts/server-routes)
                       (assoc-in [:option :headers :x-csrf-token] csrf-token))]
        (invoke-http-requester service effect)))))

#?(:cljs
   (listen-to
    :http-requester/request-authentication
    (fn [service {:keys [credential]}]
      [{:effect/id      :http-requester/call-server
        :request-method :post
        :route          :auth/login
        :option
        {:params credential
         :success-event
         {:event/id :http-requester/authentication-callback}}}])))

#?(:cljs
   (listen-to
    :http-requester/authentication-callback
    (fn [service {:keys [response/success? response/data] :as response}]
      (when success?
        [{:effect/id :ws-server/reconnect}]))))
