(ns build
  "clojure -T:build uber → target/bikerent.jar"
  (:require [clojure.tools.build.api :as b]))

(def ^:private class-dir "target/classes")
(def ^:private uber-file "target/bikerent.jar")

(defn uber [_]
  (b/delete {:path "target"})
  (let [basis (b/create-basis {:project "deps.edn"})]
    (b/copy-dir {:src-dirs ["src" "resources"] :target-dir class-dir})
    (b/compile-clj {:basis basis :ns-compile '[bikerent.main] :class-dir class-dir})
    (b/uber {:class-dir class-dir :uber-file uber-file :basis basis :main 'bikerent.main})))
