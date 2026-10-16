(ns bikerent.http.listings
  (:require [bikerent.listings.domain :as domain]
            [bikerent.listings.logic :as logic]))

(def ^:private Data [:re {:error/message "use o formato AAAA-MM-DD"} #"^\d{4}-\d{2}-\d{2}$"])

(def ^:private campos
  [[:titulo [:string {:min 3 :max 120}]]
   [:descricao {:optional true} [:string {:max 2000}]]
   [:tipo (into [:enum] domain/tipos)]
   [:aro {:optional true} [:int {:min 12 :max 29}]]
   [:tamanho {:optional true} (into [:enum] domain/tamanhos)]
   [:diaria_centavos [:int {:min 1}]]
   [:cidade [:string {:min 2 :max 100}]]
   [:lat {:optional true} number?]
   [:lng {:optional true} number?]])

(def ^:private NovoAnuncio (into [:map] campos))

(def ^:private Alteracoes
  (into [:map [:status {:optional true} (into [:enum] domain/status)]]
        (map (fn [[k & more]]
               (if (map? (first more))
                 (into [k (assoc (first more) :optional true)] (rest more))
                 (into [k {:optional true}] more))))
        campos))

(def ^:private Filtros
  [:map
   [:cidade {:optional true} :string]
   [:tipo {:optional true} (into [:enum] domain/tipos)]
   [:preco_min {:optional true} :int]
   [:preco_max {:optional true} :int]
   [:inicio {:optional true} Data]
   [:fim {:optional true} Data]])

(defn- ok [body] {:status 200 :body body})

(defn routes [deps]
  [["/listings"
    {:get  {:parameters {:query Filtros}
            :handler    (fn [{{:keys [query]} :parameters}]
                          (ok (logic/search deps query)))}
     :post {:login?     true
            :parameters {:body NovoAnuncio}
            :handler    (fn [{:keys [identity] {:keys [body]} :parameters}]
                          {:status 201 :body (logic/create! deps identity body)})}}]

   ["/listings/:id"
    {:parameters {:path [:map [:id :int]]}
     :get        {:handler (fn [{{{:keys [id]} :path} :parameters}]
                             (ok (logic/get-listing deps id)))}
     :put        {:login?     true
                  :parameters {:body Alteracoes}
                  :handler    (fn [{:keys [identity] {{:keys [id]} :path :keys [body]} :parameters}]
                                (ok (logic/update! deps identity id body)))}}]

   ["/listings/:id/photos"
    {:login?     true
     :parameters {:path [:map [:id :int]]}
     :post       {:parameters {:body [:map [:url [:re #"^https?://.+"]]]}
                  :handler    (fn [{:keys [identity] {{:keys [id]} :path {:keys [url]} :body} :parameters}]
                                {:status 201 :body (logic/add-photo! deps identity id url)})}}]

   ["/listings/:id/photos/:photo-id"
    {:login?     true
     :parameters {:path [:map [:id :int] [:photo-id :int]]}
     :delete     {:handler (fn [{:keys [identity] {{:keys [id photo-id]} :path} :parameters}]
                             (logic/remove-photo! deps identity id photo-id)
                             {:status 204})}}]

   ["/listings/:id/availability-blocks"
    {:login?     true
     :parameters {:path [:map [:id :int]]}
     :post       {:parameters {:body [:map [:inicio Data] [:fim Data]]}
                  :handler    (fn [{:keys [identity] {{:keys [id]} :path :keys [body]} :parameters}]
                                {:status 201 :body (logic/add-block! deps identity id body)})}}]

   ["/listings/:id/availability-blocks/:block-id"
    {:login?     true
     :parameters {:path [:map [:id :int] [:block-id :int]]}
     :delete     {:handler (fn [{:keys [identity] {{:keys [id block-id]} :path} :parameters}]
                             (logic/remove-block! deps identity id block-id)
                             {:status 204})}}]

   ["/me/listings"
    {:login?     true
     :get        {:handler (fn [{:keys [identity]}]
                             (ok (logic/mine deps identity)))}}]])
