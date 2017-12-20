(ns kos.impl.listener
  #?(:clj
     (:require
      [taoensso.encore :refer [do-true]]
      [clojure.core.async :as asn :refer [go-loop]])
     :cljs
     (:require
      [taoensso.encore :refer-macros [do-true]]
      [cljs.core.async :as asn]))
  #?(:cljs
     (:require-macros
      [cljs.core.async.macros :refer [go-loop]])))

(defn go-react!
  [to-listen-ch stop-ch handler]
  (do-true
   (go-loop []
     (let [[event ch] (asn/alts! [to-listen-ch stop-ch] :priority true)
           stop? (or (= stop-ch ch) (nil? event))]
       (when-not stop?
         (handler event)
         (recur))))))
