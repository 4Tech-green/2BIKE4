(ns user
  (:require [bikerent.system :as system]
            [integrant.repl :as ir]
            [integrant.repl.state :as state]))

(ir/set-prep! system/config)

(def go ir/go)
(def halt ir/halt)
(def reset ir/reset)

(defn ds
  "Datasource do sistema em execução, para consultas no REPL."
  []
  (:bikerent.db/datasource state/system))
