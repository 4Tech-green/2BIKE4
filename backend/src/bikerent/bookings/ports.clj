(ns bikerent.bookings.ports)

(defprotocol BookingRepository
  (create! [repo booking] "Insere a reserva e devolve o id.")
  (find-by-id [repo id] "Reserva com dados do anúncio, das pessoas e do pagamento, ou nil.")
  (list-by-renter [repo user-id] "Reservas feitas pelo usuário.")
  (list-by-owner [repo user-id] "Reservas recebidas nas bikes do usuário.")
  (available? [repo listing-id periodo] "true se não há bloqueio nem reserva confirmada no período.")
  (update-status! [repo id de para] "Muda o status se ainda for `de`; true se mudou."))
