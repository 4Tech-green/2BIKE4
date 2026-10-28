(ns bikerent.bookings.adapters.postgres
  (:require [bikerent.bookings.ports :as ports]
            [bikerent.db :as db]
            [clojure.string :as str]
            [integrant.core :as ig]))

(def ^:private consulta
  {:select    (into [:b.id :b.listing_id :b.renter_id :b.total_centavos :b.status :b.criado_em
                     :l.owner_id :l.titulo :l.cidade
                     [:r.nome :locatario_nome] [:d.nome :dono_nome] [:p.status :pagamento]
                     [{:select [:url] :from :listing_photos :where [:= :listing_id :b.listing_id]
                       :order-by [:ordem :id] :limit 1}
                      :foto]]
                    (db/periodo-cols :b.periodo))
   :from      [[:bookings :b]]
   :join      [[:listings :l] [:= :l.id :b.listing_id]
               [:users :r] [:= :r.id :b.renter_id]
               [:users :d] [:= :d.id :l.owner_id]]
   :left-join [[:payments :p] [:= :p.booking_id :b.id]]})

(defn- com-avaliacoes-de
  "Acrescenta :ja_avaliei, os alvos que `autor-id` já avaliou em cada reserva,
  para a tela saber quando mostrar o botão de avaliar."
  [query autor-id]
  (update query :select conj
          [{:select [[[:string_agg :alvo ","]]] :from :reviews
            :where  [:and [:= :booking_id :b.id] [:= :autor_id autor-id]]}
           :ja_avaliei]))

(defn- separar-avaliacoes [booking]
  (update booking :ja_avaliei #(if % (str/split % #",") [])))

(defrecord PostgresBookingRepository [ds]
  ports/BookingRepository
  (create! [_ {:keys [periodo] :as booking}]
    (:id (db/execute-one! ds {:insert-into :bookings
                              :values      [(assoc booking :periodo (db/daterange periodo))]
                              :returning   [:id]})))

  (find-by-id [_ id]
    (db/execute-one! ds (assoc consulta :where [:= :b.id id])))

  (list-by-renter [_ user-id]
    (mapv separar-avaliacoes
          (db/execute! ds (-> (com-avaliacoes-de consulta user-id)
                              (assoc :where [:= :b.renter_id user-id]
                                     :order-by [[[:lower :b.periodo] :desc]])))))

  (list-by-owner [_ user-id]
    (mapv separar-avaliacoes
          (db/execute! ds (-> (com-avaliacoes-de consulta user-id)
                              (assoc :where [:= :l.owner_id user-id]
                                     :order-by [[:b.criado_em :desc]])))))

  (available? [_ listing-id periodo]
    (let [sobrepoe [:&& :periodo (db/daterange periodo)]]
      (not (:ocupado (db/execute-one!
                      ds {:select [[[:or
                                     [:exists {:select [1] :from :bookings
                                               :where  [:and [:= :listing_id listing-id]
                                                        [:in :status ["confirmed" "completed"]]
                                                        sobrepoe]}]
                                     [:exists {:select [1] :from :availability_blocks
                                               :where  [:and [:= :listing_id listing-id] sobrepoe]}]]
                                    :ocupado]]})))))

  (update-status! [_ id de para]
    (pos? (:next.jdbc/update-count
           (db/execute-one! ds {:update :bookings :set {:status para}
                                :where  [:and [:= :id id] [:= :status de]]})))))

(defmethod ig/init-key ::repository [_ {:keys [datasource]}]
  (->PostgresBookingRepository datasource))
