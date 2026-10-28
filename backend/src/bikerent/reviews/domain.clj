(ns bikerent.reviews.domain
  (:require [bikerent.bookings.domain :as bookings]))

(def alvos ["listing" "owner" "renter"])

(defn alvos-permitidos
  "Locatário avalia a bike e o dono; o dono avalia o locatário."
  [booking user]
  (cond
    (bookings/locatario? booking user) #{"listing" "owner"}
    (bookings/dono? booking user)      #{"renter"}
    :else                              #{}))
