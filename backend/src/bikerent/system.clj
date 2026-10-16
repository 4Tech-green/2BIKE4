(ns bikerent.system
  "Integrant: lê resources/config.edn e monta o sistema."
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [integrant.core :as ig]))

(defmethod aero/reader 'ig/ref [_ _ value]
  (ig/ref value))

(defn config []
  (let [cfg (aero/read-config (io/resource "config.edn"))]
    (ig/load-namespaces cfg)
    cfg))

(defn start []
  (ig/init (config)))

(defn stop [system]
  (ig/halt! system))
