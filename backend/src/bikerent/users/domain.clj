(ns bikerent.users.domain
  (:require [clojure.string :as str]))

(defn normalize-email [email]
  (-> email str/trim str/lower-case))

(defn public
  "Dados do usuário que podem sair pela API (sem o hash da senha)."
  [user]
  (select-keys user [:id :nome :email :papel :criado_em]))

(defn admin? [user]
  (= "admin" (:papel user)))
