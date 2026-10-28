(ns bikerent.reviews.logic
  "Casos de uso de avaliações. `deps` traz {:reviews :bookings :listings :events}."
  (:require [bikerent.bookings.logic :as bookings]
            [bikerent.listings.logic :as listings]
            [bikerent.reviews.domain :as domain]
            [bikerent.reviews.ports :as ports]
            [bikerent.shared.errors :as errors]
            [bikerent.shared.events :as events]))

(defn submit! [{:keys [reviews events] :as deps} user booking-id {:keys [alvo nota comentario]}]
  (let [booking (bookings/get-booking deps user booking-id)]
    (when-not ((domain/alvos-permitidos booking user) alvo)
      (errors/error! :forbidden "Você não pode fazer esta avaliação nesta reserva"))
    (when-not (= "completed" (:status booking))
      (errors/error! :conflict "Só dá para avaliar depois que a reserva for concluída"))
    (let [review (or (ports/create! reviews (cond-> {:booking_id booking-id :autor_id (:id user)
                                                     :alvo alvo :nota nota}
                                              comentario (assoc :comentario comentario)))
                     (errors/error! :conflict "Você já fez esta avaliação"))]
      (events/publish! events :review-submitted (select-keys review [:id :booking_id :alvo :nota]))
      review)))

(defn for-listing [{:keys [reviews] :as deps} listing-id]
  (listings/get-listing deps listing-id)
  (ports/list-by-listing reviews listing-id))
