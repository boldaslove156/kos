(ns kos.element.listener
  (:require
   [kos.event.listener :refer [act-to]]))

(act-to
 :element/mount
 (fn [{:keys [element]} {:keys [db-db]}]
   ((:mount-fn element) db-db)))
