(ns bikerent.main
  (:require [bikerent.system :as system])
  (:gen-class))

(defn -main [& _]
  (let [sys (system/start)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable #(system/stop sys)))))
