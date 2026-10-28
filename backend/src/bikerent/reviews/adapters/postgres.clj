(ns bikerent.reviews.adapters.postgres
  (:require [bikerent.db :as db]
            [bikerent.reviews.ports :as ports]
            [integrant.core :as ig]))

(defrecord PostgresReviewRepository [ds]
  ports/ReviewRepository
  (create! [_ review]
    (db/execute-one! ds {:insert-into :reviews
                         :values      [review]
                         :on-conflict [:booking_id :autor_id :alvo]
                         :do-nothing  true
                         :returning   [:*]}))

  (list-by-listing [_ listing-id]
    (db/execute! ds {:select   [:rv.id :rv.nota :rv.comentario :rv.criado_em [:u.nome :autor_nome]]
                     :from     [[:reviews :rv]]
                     :join     [[:bookings :b] [:= :b.id :rv.booking_id]
                                [:users :u] [:= :u.id :rv.autor_id]]
                     :where    [:and [:= :b.listing_id listing-id] [:= :rv.alvo "listing"]]
                     :order-by [[:rv.criado_em :desc]]})))

(defmethod ig/init-key ::repository [_ {:keys [datasource]}]
  (->PostgresReviewRepository datasource))
