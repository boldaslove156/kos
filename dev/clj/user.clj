(ns user
  (:require
   [mount.core :as mnt]
   [figwheel-sidecar.repl-api :as fwra]
   [kos.app]))

(def config
  {:server-port 9090
   :builds
   {:dev-complete
    {:source-paths ["src/cljs" "src/cljc"]
     :figwheel     true
     :compiler     {:main          "kos.app"
                    :output-to     "resources/public/js/kos/app.js"
                    :output-dir    "resources/public/js/kos/out"
                    :source-map    true
                    :optimizations :none
                    :pretty-print  true}}}})



(defn start!
  []
  (do (mnt/start-with-args {:profile :dev})
      (fwra/start-figwheel! config)))

(defn stop!
  []
  (do (mnt/stop)
      (fwra/stop-figwheel!)))

(defn browser-repl!
  []
  (fwra/cljs-repl))
