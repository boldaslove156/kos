(ns kos.ws
  #?(:clj
     (:require
      [clojure.core.async :as asn]
      [mount.core :refer [defstate]]
      [taoensso.sente.server-adapters.aleph :refer [get-sch-adapter]]
      [taoensso.sente.packers.transit :refer [get-transit-packer]]
      [taoensso.sente :as snt]
      [taoensso.timbre :as tmb]
      [taoensso.encore :refer [have]])
     :cljs
     (:require
      [cljs.core.async :as asn]
      [mount.core :refer-macros [defstate]]
      [taoensso.sente.packers.transit :refer [get-transit-packer]]
      [taoensso.sente :as snt]
      [taoensso.timbre :as tmb :include-macros true]
      [taoensso.encore :refer-macros [have]]
      [kos.routes :as rts])))

(defn boot-ws-server!
  []
  (tmb/info "Booting up web socket server...")
  (let [packer (get-transit-packer)]
    #?(:clj (snt/make-channel-socket!
             (get-sch-adapter)
             {:packer packer})
       :cljs (snt/make-channel-socket!
              (rts/path-for :server :ws/ajax {})
              {:packer         packer
               :type           :auto
               :wrap-recv-evs? false}))))

(declare listen-chan)

(defn shutdown-ws-server!
  [ws-server]
  (tmb/info "Shutting down web socket server...")
  (asn/close! (listen-chan ws-server)))

(defstate ws-server
  :start (boot-ws-server!)
  :stop (shutdown-ws-server! @ws-server))

(defn listen-chan
  [ws-server]
  (:ch-recv ws-server))

#?(:clj
   (defn ring-get
     [ws-server]
     (:ajax-get-or-ws-handshake-fn ws-server)))

#?(:clj
   (defn ring-post
     [ws-server]
     (:ajax-post-fn ws-server)))

#?(:cljs
   (defn reconnect!
     [ws-server]
     (snt/chsk-reconnect! (:chsk ws-server))))

(defn bootstrap-outgoing-event
 [event]
 [(have (:event/id event))
  (dissoc event :event/id)])

#?(:clj
   (defn publish!
     ([ws-server event]
      (let [uids (:connected-uids ws-server)]
        (run! #(publish! ws-server % event) (:any @uids))))
     ([ws-server user-id event]
      (let [send-fn (:send-fn ws-server)]
        (send-fn user-id (bootstrap-outgoing-event event)))))
   :cljs
   (defn publish!
     [ws-server event & more]
     (let [send-fn (:send-fn ws-server)]
       (apply send-fn (bootstrap-outgoing-event event) more))))
