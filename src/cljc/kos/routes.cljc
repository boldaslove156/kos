(ns kos.routes
  (:require
   [bidi.bidi :as bd]))

(def server-routes
  ["" [["/chsk" :ws/ajax]
       ["/resources/public" [[true :static-files/asset]]]
       ["/login" :auth/login]
       ["/" :static-files/index]
       [true :app/not-found]]])

(def client-routes
  ["" [["/" :client/index]
       [true :app/not-found]]])

(defn path-for
  [what target params]
  (let [routes (case what
                 :server server-routes
                 :client client-routes)
        params (mapcat identity params)]
    (apply bd/path-for routes target params)))
