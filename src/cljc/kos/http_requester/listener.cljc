(ns kos.http-requester.listener
  #?(:clj
     (:require
      [taoensso.encore :as enc]
      [kos.event.listener :refer [act-to listen-to]]
      [kos.routes :as rts]
      [kos.event :as evt])
     :cljs
     (:require
      [taoensso.encore :as enc :include-macros true]
      [kos.event.listener :refer [act-to listen-to]]
      [kos.routes :as rts]
      [kos.ws :as ws]
      [kos.event :as evt]
      [kos.db.domain.user :as db.dmn.usr])))

(defn callback-handler
  [event-dispatcher event response]
  (let [event (enc/merge event response)]
    (evt/dispatch! event-dispatcher event)))

(defn invoke-http-requester
  [{:keys [http-req event-dispatcher]}
   {:keys [routes route params request-method option]
    :or   {request-method :get
           params         {}}}]
  (let [success-event (enc/have (:success-event option))
        error-event   (:error-event option success-event)
        option        (-> option
                          (dissoc :success-event :error-event)
                          (enc/merge {:handler       (partial callback-handler
                                                        event-dispatcher
                                                        success-event)
                                      :error-handler (partial callback-handler
                                                        event-dispatcher
                                                        error-event)}))]
    (http-req routes route params request-method option)))

#?(:cljs
   (act-to
    :http-requester/call-server
    (fn [{:keys [ws-server] :as service} effect]
      (let [csrf-token (ws/csrf-token ws-server)
            effect     (-> effect
                           (assoc :routes rts/server-routes)
                           (assoc-in [:option :headers :x-csrf-token]
                                     csrf-token))]
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
    :http-requester/request-unauthentication
    (fn [service event]
      [{:effect/id      :http-requester/call-server
        :request-method :post
        :route          :auth/logout
        :option
        {:success-event
         {:event/id :http-requester/unauthentication-callback}}}])))

#?(:cljs
   (listen-to
    :http-requester/authentication-callback
    (fn [service {:keys [response/success? response/data] :as response}]
      (when success?
        [{:effect/id :ws-server/reconnect}]))))

#?(:cljs
   (listen-to
    :http-requester/unauthentication-callback
    (fn [{:keys [db-db]}
        {:keys [response/success? response/data] :as response}]
      (when success?
        [(let [self-user (db.dmn.usr/find-self-user db-db)
               user-eid  (find self-user :db.entity/id)]
           {:effect/id :db-conn/transact
            :tx-data   [[:db/retract user-eid :user/me? true]]
            :tx-meta   {:db/sync? false}})
         {:effect/id :ws-server/reconnect}]))))
