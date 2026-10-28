(ns bikerent.payments.adapters.fake
  "Gateway simulado: aprova toda cobrança e todo estorno, sem dinheiro de verdade."
  (:require [bikerent.payments.ports :as ports]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defrecord FakeGateway []
  ports/PaymentGateway
  (charge! [_ cobranca]
    (log/info "Pagamento simulado aprovado" cobranca)
    {:status "captured"})
  (refund! [_ pagamento]
    (log/info "Estorno simulado" pagamento)
    {:status "refunded"}))

(defmethod ig/init-key ::gateway [_ _]
  (->FakeGateway))
