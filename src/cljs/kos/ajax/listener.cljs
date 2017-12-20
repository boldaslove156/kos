(ns kos.ajax.listener
  (:require
   [taoensso.encore :as enc :include-macros true]
   [kos.event.listener :refer [act-to listen-to]]))

(act-to
 :ajax-caller/call
 (fn [{:keys [ajax-caller]} {:keys [target params option callback-event]
                            :or   {params {} option {}}}]
   {:pre [(enc/have? some? target callback-event)]}
   (ajax-caller target params option callback-event)))

;; TODO: csrf-token from @ws-server
(listen-to
 :ajax-caller/request-authentication
 (fn [service {:keys [credential]}]
   [{:effect/id      :ajax-caller/call
     :target         :auth/login
     :option         {:method :post
                      :params credential}
     :callback-event {:event/id :ajax-caller/authentication-callback}}]))

(listen-to
 :ajax-caller/authentication-callback
 (fn [service {:keys [success?] :as response}]
   (println response)
   (when success?
     [{:effect/id :ws-server/reconnect}])))
