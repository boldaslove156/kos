(ns kos.app
  (:require
   [mount.core :as mnt]
   [kos.config]
   [kos.http-requester]
   [kos.http-requester.listener]
   [kos.db]
   [kos.db.bootstrapper]
   [kos.db.listener]
   [kos.event]
   [kos.event.listener]
   [kos.ws]
   [kos.ws.listener]
   [kos.router]
   [kos.element]
   [kos.element.listener]))

(enable-console-print!)

(mnt/start)
