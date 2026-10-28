(ns bikerent.payments.ports)

(defprotocol PaymentGateway
  (charge! [gateway cobranca] "Cobra {:booking_id :valor_centavos}; devolve {:status \"captured\"} ou {:status \"declined\"}.")
  (refund! [gateway pagamento] "Estorna um pagamento capturado."))

(defprotocol PaymentRepository
  (create! [repo pagamento])
  (mark-refunded! [repo booking-id]))
