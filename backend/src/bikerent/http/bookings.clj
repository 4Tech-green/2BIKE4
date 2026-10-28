(ns bikerent.http.bookings
  (:require [bikerent.bookings.logic :as logic]
            [bikerent.http.listings :refer [Data]]
            [bikerent.reviews.domain :as reviews-domain]
            [bikerent.reviews.logic :as reviews]))

(defn- ok [body] {:status 200 :body body})

(defn- acao
  "Rota POST /bookings/:id/<nome> que aplica uma ação e devolve a reserva."
  [deps nome f]
  [(str "/bookings/:id/" nome)
   {:login?     true
    :parameters {:path [:map [:id :int]]}
    :post       {:handler (fn [{:keys [identity] {{:keys [id]} :path} :parameters}]
                            (ok (f deps identity id)))}}])

(defn routes [deps]
  [["/bookings"
    {:login? true
     :post   {:parameters {:body [:map [:listing_id :int] [:inicio Data] [:fim Data]]}
              :handler    (fn [{:keys [identity] {:keys [body]} :parameters}]
                            {:status 201 :body (logic/create! deps identity body)})}}]

   ;; /mine e /received casam também com /:id; :conflicting faz o Reitit
   ;; testar as três na ordem em que aparecem aqui.
   ["/bookings/mine"
    {:conflicting true
     :login?      true
     :get         {:handler (fn [{:keys [identity]}] (ok (logic/mine deps identity)))}}]

   ["/bookings/received"
    {:conflicting true
     :login?      true
     :get         {:handler (fn [{:keys [identity]}] (ok (logic/received deps identity)))}}]

   ["/bookings/:id"
    {:conflicting true
     :login?      true
     :parameters  {:path [:map [:id :int]]}
     :get         {:handler (fn [{:keys [identity] {{:keys [id]} :path} :parameters}]
                              (ok (logic/get-booking deps identity id)))}}]

   (acao deps "confirm" logic/confirm!)
   (acao deps "reject" logic/reject!)
   (acao deps "cancel" logic/cancel!)
   (acao deps "pay" logic/pay!)
   (acao deps "complete" logic/complete!)

   ["/bookings/:id/reviews"
    {:login?     true
     :parameters {:path [:map [:id :int]]}
     :post       {:parameters {:body [:map
                                      [:alvo (into [:enum] reviews-domain/alvos)]
                                      [:nota [:int {:min 1 :max 5}]]
                                      [:comentario {:optional true} [:string {:max 1000}]]]}
                  :handler    (fn [{:keys [identity] {{:keys [id]} :path :keys [body]} :parameters}]
                                {:status 201 :body (reviews/submit! deps identity id body)})}}]

   ["/listings/:id/reviews"
    {:parameters {:path [:map [:id :int]]}
     :get        {:handler (fn [{{{:keys [id]} :path} :parameters}]
                             (ok (reviews/for-listing deps id)))}}]])
