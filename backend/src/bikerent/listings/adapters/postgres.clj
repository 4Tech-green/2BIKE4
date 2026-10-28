(ns bikerent.listings.adapters.postgres
  (:require [bikerent.db :as db]
            [bikerent.listings.ports :as ports]
            [clojure.string :as str]
            [honey.sql :as sql]
            [integrant.core :as ig]))

(def ^:private cols
  [:l.id :l.owner_id :l.titulo :l.descricao :l.tipo :l.aro :l.tamanho
   :l.diaria_centavos :l.cidade :l.lat :l.lng :l.status :l.criado_em])

(def ^:private foto-capa
  [{:select [:url] :from :listing_photos :where [:= :listing_id :l.id] :order-by [:ordem :id] :limit 1}
   :foto])

(def ^:private avaliacao
  (let [das-avaliacoes {:from  [[:reviews :rv]]
                        :join  [[:bookings :bk] [:= :bk.id :rv.booking_id]]
                        :where [:and [:= :bk.listing_id :l.id] [:= :rv.alvo "listing"]]}]
    [[(assoc das-avaliacoes :select [[[:round [:avg :rv.nota] [:inline 1]]]]) :nota_media]
     [(assoc das-avaliacoes :select [[[:count :*]]]) :total_avaliacoes]]))

(def ^:private ocupa ["confirmed" "completed"])

;; distância de cosseno do pgvector
(sql/register-op! :<=>)

(defn- pgvector [vetor]
  [:cast (str "[" (str/join "," vetor) "]") :vector])

(defn- livre-no-periodo [periodo]
  [:and
   [:not [:exists {:select [1] :from :bookings
                   :where  [:and [:= :bookings.listing_id :l.id]
                            [:in :bookings.status ocupa]
                            [:&& :bookings.periodo (db/daterange periodo)]]}]]
   [:not [:exists {:select [1] :from :availability_blocks
                   :where  [:and [:= :availability_blocks.listing_id :l.id]
                            [:&& :availability_blocks.periodo (db/daterange periodo)]]}]]])

(defn- futuros [tabela listing-id & condicoes]
  {:select   (into [:id] (db/periodo-cols :periodo))
   :from     tabela
   :where    (into [:and [:= :listing_id listing-id] [:>= [:upper :periodo] [:raw "current_date"]]] condicoes)
   :order-by [[[:lower :periodo]]]})

(defrecord PostgresListingRepository [ds]
  ports/ListingRepository
  (create! [_ listing]
    (:id (db/execute-one! ds {:insert-into :listings :values [listing] :returning [:id]})))

  (update! [_ id changes]
    (when (seq changes)
      (db/execute-one! ds {:update :listings :set changes :where [:= :id id]})))

  (find-by-id [_ id]
    (when-let [listing (db/execute-one! ds {:select (-> cols (conj [:u.nome :dono_nome]) (into avaliacao))
                                            :from   [[:listings :l]]
                                            :join   [[:users :u] [:= :u.id :l.owner_id]]
                                            :where  [:= :l.id id]})]
      (assoc listing
             :fotos (db/execute! ds {:select [:id :url :ordem] :from :listing_photos
                                     :where [:= :listing_id id] :order-by [:ordem :id]})
             :bloqueios (db/execute! ds (futuros :availability_blocks id))
             :reservados (mapv #(dissoc % :id)
                               (db/execute! ds (futuros :bookings id [:in :status ocupa]))))))

  (search [_ {:keys [cidade tipo preco_min preco_max periodo]}]
    (db/execute! ds {:select   (-> cols (conj foto-capa) (into avaliacao))
                     :from     [[:listings :l]]
                     :where    (into [:and [:= :l.status "publicado"]]
                                     (remove nil?)
                                     [(when cidade [:ilike :l.cidade (str "%" cidade "%")])
                                      (when tipo [:= :l.tipo tipo])
                                      (when preco_min [:>= :l.diaria_centavos preco_min])
                                      (when preco_max [:<= :l.diaria_centavos preco_max])
                                      (when periodo (livre-no-periodo periodo))])
                     :order-by [[:l.criado_em :desc]]
                     :limit    100}))

  (list-by-owner [_ owner-id]
    (db/execute! ds {:select   (conj cols foto-capa)
                     :from     [[:listings :l]]
                     :where    [:= :l.owner_id owner-id]
                     :order-by [[:l.criado_em :desc]]}))

  (add-photo! [_ listing-id url]
    (let [{:keys [n]} (db/execute-one! ds {:select [[[:count :*] :n]] :from :listing_photos
                                           :where [:= :listing_id listing-id]})]
      (db/execute-one! ds {:insert-into :listing_photos
                           :values      [{:listing_id listing-id :url url :ordem n}]
                           :returning   [:id :url :ordem]})))

  (remove-photo! [_ listing-id photo-id]
    (pos? (:next.jdbc/update-count
           (db/execute-one! ds {:delete-from :listing_photos
                                :where [:and [:= :id photo-id] [:= :listing_id listing-id]]}))))

  (add-block! [_ listing-id periodo]
    (:id (db/execute-one! ds {:insert-into :availability_blocks
                              :values      [{:listing_id listing-id :periodo (db/daterange periodo)}]
                              :returning   [:id]})))

  (remove-block! [_ listing-id block-id]
    (pos? (:next.jdbc/update-count
           (db/execute-one! ds {:delete-from :availability_blocks
                                :where [:and [:= :id block-id] [:= :listing_id listing-id]]}))))

  (save-embedding! [_ listing-id vetor]
    (db/execute-one! ds {:update :listings :set {:embedding (pgvector vetor)} :where [:= :id listing-id]}))

  (ids-without-embedding [_]
    (mapv :id (db/execute! ds {:select [:id] :from :listings :where [:= :embedding nil]})))

  (search-similar [_ vetor limite]
    (let [similaridade [:- [:inline 1] [:<=> :l.embedding (pgvector vetor)]]]
      (db/execute! ds {:select   (-> cols (conj foto-capa) (into avaliacao) (conj [similaridade :similaridade]))
                       :from     [[:listings :l]]
                       :where    [:and [:= :l.status "publicado"] [:<> :l.embedding nil] [:> similaridade 0]]
                       :order-by [[:similaridade :desc]]
                       :limit    limite}))))

(defmethod ig/init-key ::repository [_ {:keys [datasource]}]
  (->PostgresListingRepository datasource))
