(ns bikerent.http.search
  (:require [bikerent.search.logic :as logic]))

(defn routes [deps]
  [["/search"
    {:get {:parameters {:query [:map [:q [:string {:min 2 :max 200}]]]}
           :handler    (fn [{{{:keys [q]} :query} :parameters}]
                         {:status 200 :body (logic/search deps q)})}}]])
