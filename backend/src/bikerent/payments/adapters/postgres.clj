(ns bikerent.payments.adapters.postgres
  (:require [bikerent.db :as db]
            [bikerent.payments.ports :as ports]
            [integrant.core :as ig]))

(defrecord PostgresPaymentRepository [ds]
  ports/PaymentRepository
  (create! [_ pagamento]
    (db/execute-one! ds {:insert-into :payments :values [pagamento] :returning [:*]}))
  (mark-refunded! [_ booking-id]
    (db/execute-one! ds {:update :payments :set {:status "refunded"} :where [:= :booking_id booking-id]})))

(defmethod ig/init-key ::repository [_ {:keys [datasource]}]
  (->PostgresPaymentRepository datasource))
