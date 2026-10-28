(ns bikerent.bookings.logic-test
  (:require [bikerent.bookings.domain :as domain]
            [bikerent.bookings.logic :as logic]
            [bikerent.bookings.ports :as ports]
            [bikerent.listings.ports :as listings]
            [bikerent.payments.ports :as payments]
            [bikerent.reviews.logic :as reviews]
            [bikerent.reviews.ports :as reviews-ports]
            [bikerent.shared.errors :as errors]
            [bikerent.shared.events :as events]
            [bikerent.shared.time :as time]
            [clojure.test :refer [deftest is testing]]))

(def ^:private dono {:id 10 :papel "user"})
(def ^:private locatario {:id 20 :papel "user"})
(def ^:private estranho {:id 30 :papel "user"})
(def ^:private admin {:id 40 :papel "admin"})

(defn- deps-em-memoria
  "Adaptadores em memória. A bike 1 é publicada, a 2 está pausada; `livre?`
  responde à checagem de disponibilidade."
  [& {:keys [livre?] :or {livre? true}}]
  (let [reservas (atom {})
        pagtos   (atom {})
        avals    (atom #{})
        eventos  (atom [])
        bikes    {1 {:id 1 :owner_id 10 :diaria_centavos 3000 :status "publicado"}
                  2 {:id 2 :owner_id 10 :diaria_centavos 3000 :status "pausado"}}]
    {:eventos  eventos
     :pagtos   pagtos
     :listings (reify listings/ListingRepository
                 (find-by-id [_ id] (get bikes id)))
     :bookings (reify ports/BookingRepository
                 (create! [_ b]
                   (let [id (inc (count @reservas))]
                     (swap! reservas assoc id (assoc b :id id :status "pending"))
                     id))
                 (find-by-id [_ id]
                   (when-let [b (get @reservas id)]
                     (assoc b
                            :owner_id (:owner_id (get bikes (:listing_id b)))
                            :pagamento (:status (get @pagtos id)))))
                 (available? [_ _ _] livre?)
                 (update-status! [_ id de para]
                   (when (= de (:status (get @reservas id)))
                     (swap! reservas assoc-in [id :status] para)
                     true)))
     :payments (reify payments/PaymentRepository
                 (create! [_ p] (swap! pagtos assoc (:booking_id p) p))
                 (mark-refunded! [_ id] (swap! pagtos assoc-in [id :status] "refunded")))
     :gateway  (reify payments/PaymentGateway
                 (charge! [_ _] {:status "captured"})
                 (refund! [_ _] {:status "refunded"}))
     :reviews  (reify reviews-ports/ReviewRepository
                 (create! [_ r]
                   (let [chave (select-keys r [:booking_id :autor_id :alvo])]
                     (when-not (@avals chave)
                       (swap! avals conj chave)
                       r))))
     :events   (reify events/EventPublisher
                 (publish! [_ tipo _] (swap! eventos conj tipo)))}))

(defn- tipo-do-erro [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e (errors/error-type e))))

(defn- daqui-a [dias]
  (str (.plusDays (time/hoje) dias)))

(defn- reservar! [deps]
  (logic/create! deps locatario {:listing_id 1 :inicio (daqui-a 10) :fim (daqui-a 12)}))

(deftest total-pelas-diarias-inclusivas
  (let [periodo (time/periodo "2026-11-10" "2026-11-12")]
    (is (= 3 (domain/diarias periodo)))
    (is (= 9000 (domain/total-centavos periodo 3000)))
    (is (= 3000 (domain/total-centavos (time/periodo "2026-11-10" "2026-11-10") 3000)))))

(deftest criar-reserva
  (testing "pedido válido fica pending com o total calculado"
    (let [deps (deps-em-memoria)
          b    (reservar! deps)]
      (is (= "pending" (:status b)))
      (is (= 9000 (:total_centavos b)))
      (is (= [:booking-requested] @(:eventos deps)))))
  (testing "regras de criação"
    (let [deps (deps-em-memoria)]
      (is (= :bad-request (tipo-do-erro #(logic/create! deps dono {:listing_id 1 :inicio (daqui-a 1) :fim (daqui-a 2)}))))
      (is (= :conflict (tipo-do-erro #(logic/create! deps locatario {:listing_id 2 :inicio (daqui-a 1) :fim (daqui-a 2)}))))
      (is (= :not-found (tipo-do-erro #(logic/create! deps locatario {:listing_id 99 :inicio (daqui-a 1) :fim (daqui-a 2)}))))
      (is (= :bad-request (tipo-do-erro #(logic/create! deps locatario {:listing_id 1 :inicio (daqui-a -1) :fim (daqui-a 2)}))))))
  (testing "datas ocupadas"
    (is (= :conflict (tipo-do-erro #(reservar! (deps-em-memoria :livre? false)))))))

(deftest fluxo-completo
  (let [deps (deps-em-memoria)
        id   (:id (reservar! deps))]
    (is (= :forbidden (tipo-do-erro #(logic/get-booking deps estranho id))))
    (is (= :forbidden (tipo-do-erro #(logic/confirm! deps locatario id))))
    (is (= :conflict (tipo-do-erro #(logic/pay! deps locatario id))) "pagar antes de confirmar")
    (is (= "confirmed" (:status (logic/confirm! deps dono id))))
    (is (= :conflict (tipo-do-erro #(logic/reject! deps dono id))))
    (is (= :conflict (tipo-do-erro #(logic/complete! deps dono id))) "concluir antes de pagar")
    (is (= :forbidden (tipo-do-erro #(logic/pay! deps dono id))))
    (is (= "captured" (:pagamento (logic/pay! deps locatario id))))
    (is (= :conflict (tipo-do-erro #(logic/pay! deps locatario id))) "pagar duas vezes")
    (is (= "completed" (:status (logic/complete! deps admin id))))
    (is (= :conflict (tipo-do-erro #(logic/cancel! deps locatario id))))
    (is (= [:booking-requested :booking-confirmed :payment-captured :booking-completed]
           @(:eventos deps)))))

(deftest cancelar-reserva-paga-estorna
  (let [deps (deps-em-memoria)
        id   (:id (reservar! deps))]
    (logic/confirm! deps dono id)
    (logic/pay! deps locatario id)
    (let [b (logic/cancel! deps locatario id)]
      (is (= "cancelled" (:status b)))
      (is (= "refunded" (:pagamento b))))))

(deftest avaliacoes
  (let [deps (deps-em-memoria)
        id   (:id (reservar! deps))
        nota {:alvo "listing" :nota 5}]
    (is (= :conflict (tipo-do-erro #(reviews/submit! deps locatario id nota))) "antes de concluir")
    (logic/confirm! deps dono id)
    (logic/pay! deps locatario id)
    (logic/complete! deps dono id)
    (is (= 5 (:nota (reviews/submit! deps locatario id nota))))
    (is (= :conflict (tipo-do-erro #(reviews/submit! deps locatario id nota))) "uma vez por direção")
    (is (= :forbidden (tipo-do-erro #(reviews/submit! deps locatario id {:alvo "renter" :nota 4}))))
    (is (= :forbidden (tipo-do-erro #(reviews/submit! deps dono id {:alvo "owner" :nota 4}))))
    (is (= "renter" (:alvo (reviews/submit! deps dono id {:alvo "renter" :nota 4}))))
    (is (= :forbidden (tipo-do-erro #(reviews/submit! deps admin id {:alvo "listing" :nota 1}))))))
