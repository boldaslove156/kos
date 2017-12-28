(ns kos.routes
  (:require
   [bidi.bidi :as bd]))

(def server-routes
  ["" [["/chsk" :ws/ajax]
       ["/resources/public" [[true :static-files/asset]]]
       ["/login" :auth/login]
       ["/logout" :auth/logout]
       ["/" :static-files/index]
       [true :app/not-found]]])

(def client-routes
  ["" [["/" :client/index]
       [true :app/not-found]]])

(defn path-for
  ([routes route params]
   (let [params (mapcat identity params)]
     (apply bd/path-for routes route params)))
  ([routes route]
   (path-for routes route {})))
