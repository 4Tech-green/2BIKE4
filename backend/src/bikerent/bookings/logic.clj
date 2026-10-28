(ns bikerent.bookings.logic
  "Casos de uso de reservas e do pagamento simulado. `deps` traz
  {:bookings :listings :payments :gateway :events}; `user` é {:id :papel}."
  (:require [bikerent.bookings.domain :as domain]
            [bikerent.bookings.ports :as ports]
            [bikerent.listings.logic :as listings]
            [bikerent.payments.ports :as payments]
            [bikerent.shared.errors :as errors]
            [bikerent.shared.events :as events]
            [bikerent.shared.time :as time]))

(defn get-booking [{:keys [bookings]} user id]
  (let [booking (or (ports/find-by-id bookings id)
                    (errors/error! :not-found "Reserva não encontrada"))]
    (when-not (domain/pode-ver? booking user)
      (errors/error! :forbidden "Esta reserva não é sua"))
    booking))

(defn mine [{:keys [bookings]} user]
  (ports/list-by-renter bookings (:id user)))

(defn received [{:keys [bookings]} user]
  (ports/list-by-owner bookings (:id user)))

(defn create! [{:keys [bookings events] :as deps} user {:keys [listing_id inicio fim]}]
  (let [listing (listings/get-listing deps listing_id)
        periodo (time/periodo inicio fim)]
    (when (= (:owner_id listing) (:id user))
      (errors/error! :bad-request "Você não pode reservar a própria bike"))
    (when (not= "publicado" (:status listing))
      (errors/error! :conflict "Este anúncio está pausado"))
    (when (.isBefore (:inicio periodo) (time/hoje))
      (errors/error! :bad-request "A reserva não pode começar no passado"))
    (when-not (ports/available? bookings listing_id periodo)
      (errors/error! :conflict "As datas escolhidas não estão disponíveis"))
    (let [id (ports/create! bookings {:listing_id     listing_id
                                      :renter_id      (:id user)
                                      :periodo        periodo
                                      :total_centavos (domain/total-centavos periodo (:diaria_centavos listing))})]
      (events/publish! events :booking-requested {:booking_id id :listing_id listing_id})
      (get-booking deps user id))))

(defn- checar! [deps user id acao]
  (let [booking (get-booking deps user id)]
    (when-let [[tipo mensagem] (domain/erro-da-acao acao booking user)]
      (errors/error! tipo mensagem))
    booking))

(defn- mudar-status! [{:keys [bookings events] :as deps} user id acao evento]
  (let [booking (checar! deps user id acao)]
    ;; Confirmar sobre outra reserva confirmada ou sobre um bloqueio falha no
    ;; banco (23P01), que a camada HTTP traduz para 409.
    (when-not (ports/update-status! bookings id (:status booking) (get-in domain/acoes [acao :para]))
      (errors/error! :conflict "A reserva mudou enquanto isso; atualize e tente de novo"))
    (events/publish! events evento {:booking_id id})
    (get-booking deps user id)))

(defn confirm! [deps user id]
  (mudar-status! deps user id :confirm :booking-confirmed))

(defn reject! [deps user id]
  (mudar-status! deps user id :reject :booking-rejected))

(defn complete! [deps user id]
  (mudar-status! deps user id :complete :booking-completed))

(defn cancel!
  "Cancela; se já estava paga, estorna."
  [{:keys [payments gateway] :as deps} user id]
  (let [booking (mudar-status! deps user id :cancel :booking-cancelled)]
    (if (domain/pago? booking)
      (do (payments/refund! gateway booking)
          (payments/mark-refunded! payments id)
          (get-booking deps user id))
      booking)))

(defn pay! [{:keys [payments gateway events] :as deps} user id]
  (let [booking  (checar! deps user id :pay)
        cobranca {:booking_id id :valor_centavos (:total_centavos booking)}]
    (when-not (= "captured" (:status (payments/charge! gateway cobranca)))
      (errors/error! :bad-request "Pagamento recusado"))
    (payments/create! payments (assoc cobranca :status "captured"))
    (events/publish! events :payment-captured cobranca)
    (get-booking deps user id)))
