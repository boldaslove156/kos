(defproject kos "0.1.0-SNAPSHOT"
  :description  "FIXME: write description"
  :url          "http://example.com/FIXME"
  :license      {:name "Eclipse Public License"
                 :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [;; clj
                 [org.clojure/clojure "1.9.0-RC2"]
                 [com.google.guava/guava "23.0"]
                 [aero "1.1.2"]
                 [aleph "0.4.4"]
                 [com.datomic/datomic-free "0.9.5656"]
                 [datomic-schema "1.3.0"]
                 [io.rkn/conformity "0.5.1"]
                 [ring/ring-defaults "0.3.1"]
                 [metosin/ring-http-response "0.9.0"]
                 [buddy/buddy-core "1.4.0"]
                 [buddy/buddy-sign "2.2.0"]
                 [buddy/buddy-hashers "1.3.0"]
                 [com.cognitect/transit-clj "0.8.300"]
                 ;; cljs
                 [org.clojure/clojurescript "1.9.946"]
                 [com.cognitect/transit-cljs "0.8.243"]
                 ;; cljc
                 [org.clojure/core.async "0.3.465"]
                 [rum "0.10.8"]
                 [bidi "2.1.2"]
                 [datascript "0.16.3"]
                 [mount "0.1.11"]
                 [com.taoensso/sente "1.11.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.taoensso/encore "2.92.0"]]
  :source-paths ["src/clj" "src/cljc"]
  :profiles     {:dev {:clean-targets ^{:protect false}
                       [:target-path "resources/public/js/kos"]
                       :source-paths  ["src/cljs" "dev/clj"]
                       :dependencies  [[org.clojure/tools.nrepl "0.2.13"]
                                       [com.cemerick/piggieback "0.2.1"]
                                       [figwheel-sidecar "0.5.14"]]
                       :plugins       [[refactor-nrepl "2.4.0-SNAPSHOT"]
                                       [cider/cider-nrepl "0.16.0-SNAPSHOT"]]
                       :repl-options  {:nrepl-middleware
                                       [cemerick.piggieback/wrap-cljs-repl]}}}
  :aliases      {"dev" ["with-profile" "+dev" "do" "clean," "repl" ":headless"]})

;; nur: 20, 10%
;; berliana: 5
