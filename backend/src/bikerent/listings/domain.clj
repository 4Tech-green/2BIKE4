(ns bikerent.listings.domain
  (:require [bikerent.users.domain :as users]))

(def tipos ["urbana" "speed" "mountain" "hibrida" "eletrica" "infantil" "dobravel"])

(def tamanhos ["PP" "P" "M" "G" "GG"])

(def status ["publicado" "pausado"])

(defn pode-editar?
  "Só o dono (ou um admin) altera o anúncio."
  [listing user]
  (or (= (:owner_id listing) (:id user))
      (users/admin? user)))
