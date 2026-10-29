(ns seed
  "Dados de demonstração. Apaga os usuários @2bike4.test (e, em cascata, tudo
  deles) e recria pessoas, bikes em várias capitais, reservas em todos os
  estados, pagamentos e avaliações. Sempre gera os mesmos dados (semente fixa).

  DATABASE_URL=... clojure -M:seed"
  (:require [bikerent.db :as db]
            [bikerent.search.logic :as search]
            [bikerent.system :as system]
            [buddy.hashers :as hashers]
            [clojure.string :as str]
            [integrant.core :as ig])
  (:import (java.time LocalDate)
           (java.time.temporal ChronoUnit)
           (java.util Random)))

(def senha "demo1234")

(def ^:private dominio "@2bike4.test")

(def ^:private pessoas
  ["Ana Souza" "Bruno Lima" "Carla Mendes" "Diego Rocha" "Elisa Ramos" "Felipe Costa"
   "Gabriela Nunes" "Henrique Alves" "Isabela Freitas" "João Santos" "Larissa Gomes" "Marcos Vieira"])

(def ^:private cidades
  ;; [nome lat lng quantas-bikes]
  [["Recife" -8.0476 -34.8770 7] ["Olinda" -8.0089 -34.8553 3] ["São Paulo" -23.5505 -46.6333 6]
   ["Rio de Janeiro" -22.9068 -43.1729 6] ["Belo Horizonte" -19.9167 -43.9345 4]
   ["Curitiba" -25.4284 -49.2733 4] ["Porto Alegre" -30.0346 -51.2177 3] ["Salvador" -12.9777 -38.5016 3]
   ["Fortaleza" -3.7319 -38.5267 3] ["Florianópolis" -27.5954 -48.5480 3] ["Brasília" -15.7939 -47.8828 3]])

(def ^:private modelos
  ;; tipo → [[título aro tamanho diária-em-reais descrição] ...]
  {"mountain" [["Caloi Elite Carbon" 29 "M" 60 "Suspensão dianteira e 12 marchas. Ótima para trilhas leves e estrada de terra."]
               ["Oggi Big Wheel 7.0" 29 "G" 55 "Freio a disco hidráulico, pneus largos. Encara trilha pesada sem drama."]
               ["Sense Impact Pro" 29 "M" 70 "Quadro leve de alumínio, perfeita para pedal de fim de semana na serra."]
               ["Trek Marlin 7" 29 "P" 65 "Garfo com trava, ideal para quem está começando no mountain bike."]]
   "speed"    [["Specialized Allez" 700 "M" 70 "Quadro de alumínio leve, ideal para pedal na estrada e treinos longos."]
               ["Caloi Strada" 700 "G" 55 "Speed de entrada, confortável para os primeiros 50 km."]
               ["Trek Domane AL 2" 700 "M" 75 "Geometria endurance: rápida sem cansar as costas."]]
   "urbana"   [["Monark Barra Circular" 26 "G" 20 "Clássica, com cestinha. Perfeita para passear na orla."]
               ["Caloi Urbam" 26 "M" 25 "Marchas internas e para-lamas: chega ao trabalho limpo."]
               ["Houston Foxer" 26 "M" 18 "Simples e resistente, boa para o dia a dia na cidade."]]
   "hibrida"  [["Groove Hype 50" 29 "G" 35 "Boa para cidade e estradinha de terra. Acompanha capacete."]
               ["Sense Activ" 29 "M" 40 "Pneus mistos e postura confortável para passeios longos."]
               ["Trek FX 2" 700 "M" 45 "Leve e versátil: ciclovia, asfalto e um pouco de terra."]]
   "eletrica" [["Sense Impulse" 29 "M" 90 "Motor central e bateria para 80 km. Chegue ao trabalho sem suar."]
               ["Caloi E-Vibe Easy Rider" 26 "M" 85 "Elétrica urbana com assistência suave nas subidas."]
               ["Lev E-Bike T800" 26 "G" 80 "Bateria removível, carrega em casa. Ótima para ladeiras."]]
   "dobravel" [["Dahon Mu D9" 20 nil 45 "Dobra em 15 segundos, cabe no porta-malas e no metrô."]
               ["Durban Rio" 20 nil 30 "Compacta e leve, boa para combinar com ônibus e metrô."]
               ["Tern Link B7" 20 nil 50 "Dobrável de 7 marchas, roda bem em qualquer bairro."]]
   "infantil" [["Caloi Ceci" 16 nil 15 "Com rodinhas removíveis, para crianças de 4 a 7 anos."]
               ["Nathor Princess" 16 nil 15 "Com cestinha e rodinhas, para os pequenos aprenderem."]
               ["Groove Infantil" 20 nil 20 "Para crianças de 7 a 10 anos, com 6 marchas."]]})

(def ^:private comentarios-bike
  ["Bike impecável, exatamente como nas fotos." "Freios ótimos e marchas bem reguladas."
   "Muito confortável, usei o fim de semana inteiro." "Perfeita para o que eu precisava."
   "Pneus calibrados e corrente limpa. Recomendo!" "Boa bike, só o banco é um pouco duro."
   "Fez a trilha sem nenhum problema." "Ótima para passear, voltaria a alugar."])

(def ^:private comentarios-pessoa
  ["Super atencioso na entrega." "Pontual e muito simpático." "Combinou tudo direitinho."
   "Devolveu a bike limpa e no horário." "Comunicação rápida e fácil."])

;; ---------------------------------------------------------------- utilitários

(def ^:private rnd (Random. 42))

(defn- sorteio [coll] (nth coll (.nextInt rnd (count coll))))
(defn- entre [a b] (+ a (.nextInt rnd (inc (- b a)))))
(defn- chance [p] (< (.nextDouble rnd) p))
(defn- perto [coord] (+ coord (* 0.06 (- (.nextDouble rnd) 0.5))))

(defn- email [nome]
  (-> (first (str/split nome #" "))
      (java.text.Normalizer/normalize java.text.Normalizer$Form/NFD)
      (str/replace #"\p{M}" "")
      str/lower-case
      (str dominio)))

(defn- inserir! [ds tabela linha]
  (db/execute-one! ds {:insert-into tabela :values [linha] :returning [:*]}))

;; ---------------------------------------------------------------- etapas

(defn- criar-pessoas! [ds]
  (let [hash (hashers/derive senha {:alg :bcrypt+sha512})]
    (into {}
          (for [nome pessoas]
            [(first (str/split nome #" "))
             (:id (inserir! ds :users {:nome nome :email (email nome) :senha_hash hash}))]))))

(defn- criar-bikes!
  "Ana tem as bikes de Recife e Olinda; Bruno só aluga; os outros dividem o resto."
  [ds ids]
  (let [donos (vec (vals (dissoc ids "Ana" "Bruno")))]
    (vec
     (for [[cidade lat lng n] cidades
           i (range n)
           :let [tipo (sorteio (keys modelos))
                 [titulo aro tamanho diaria descricao] (sorteio (modelos tipo))
                 dono (if (#{"Recife" "Olinda"} cidade)
                        (if (< i 4) (ids "Ana") (sorteio donos))
                        (sorteio donos))]]
       (inserir! ds :listings (cond-> {:owner_id        dono
                                       :titulo          (if (= 700 aro) titulo (str titulo " aro " aro))
                                       :descricao       descricao
                                       :tipo            tipo
                                       :diaria_centavos (* 100 (+ diaria (entre -5 10)))
                                       :cidade          cidade
                                       :lat             (perto lat)
                                       :lng             (perto lng)}
                                (not= 700 aro) (assoc :aro aro)
                                tamanho        (assoc :tamanho tamanho)))))))

(defn- reservar!
  "Insere a reserva direto no banco (o seed precisa de datas no passado) e,
  conforme o estado, pagamento e avaliações."
  [ds bike renter inicio dias status & {:keys [pago? avaliada?] :or {pago? true avaliada? true}}]
  (let [fim     (.plusDays ^LocalDate inicio (dec dias))
        total   (* dias (:diaria_centavos bike))
        reserva (inserir! ds :bookings {:listing_id     (:id bike)
                                        :renter_id      renter
                                        :periodo        (db/daterange {:inicio inicio :fim fim})
                                        :total_centavos total
                                        :status         status})]
    (when (and pago? (#{"confirmed" "completed" "cancelled"} status))
      (inserir! ds :payments {:booking_id     (:id reserva)
                              :valor_centavos total
                              :status         (if (= "cancelled" status) "refunded" "captured")}))
    (when (and avaliada? (= "completed" status))
      (inserir! ds :reviews {:booking_id (:id reserva) :autor_id renter :alvo "listing"
                             :nota (sorteio [3 4 4 5 5 5]) :comentario (sorteio comentarios-bike)})
      (inserir! ds :reviews {:booking_id (:id reserva) :autor_id renter :alvo "owner"
                             :nota (sorteio [4 5 5]) :comentario (sorteio comentarios-pessoa)})
      (when (chance 0.7)
        (inserir! ds :reviews {:booking_id (:id reserva) :autor_id (:owner_id bike) :alvo "renter"
                               :nota (sorteio [4 5 5]) :comentario (sorteio comentarios-pessoa)})))
    reserva))

(defn- historico!
  "Reservas concluídas no passado (com avaliações) para dar volume às bikes."
  [ds bikes ids hoje]
  (let [locatarios (vec (vals ids))]
    (doseq [bike bikes
            :when (chance 0.75)]
      ;; períodos em sequência, do passado para hoje, sem sobrepor
      (loop [dia (.minusDays ^LocalDate hoje (entre 100 140)) n (entre 1 4)]
        (when (pos? n)
          (let [dias   (entre 1 4)
                renter (sorteio (remove #{(:owner_id bike)} locatarios))]
            ;; termina antes das reservas do roteiro (a partir de 12 dias atrás)
            (when (.isBefore (.plusDays dia dias) (.minusDays hoje 20))
              (reservar! ds bike renter dia dias (if (chance 0.85) "completed" "cancelled")))
            (recur (.plusDays dia (+ dias (entre 5 25))) (dec n))))))))

(defn- roteiro-da-demo!
  "Reservas futuras com Ana (dona) e Bruno (locatário) em cada estado, para
  mostrar todos os botões na apresentação."
  [ds bikes ids hoje]
  (let [da-ana   (filter #(= (ids "Ana") (:owner_id %)) bikes)
        de-outro (remove #(= (ids "Ana") (:owner_id %)) bikes)
        bruno    (ids "Bruno")
        dia      #(.plusDays ^LocalDate hoje %)]
    ;; Bruno alugando de outras pessoas
    (reservar! ds (nth de-outro 0) bruno (dia -12) 2 "completed" :avaliada? false) ; pode avaliar
    (reservar! ds (nth de-outro 1) bruno (dia 5) 3 "confirmed" :pago? false)        ; pode pagar
    (reservar! ds (nth de-outro 2) bruno (dia 9) 2 "confirmed")                      ; paga
    (reservar! ds (nth de-outro 3) bruno (dia 14) 2 "pending")
    ;; pedidos chegando para a Ana
    (reservar! ds (nth da-ana 0) (ids "Carla") (dia 7) 3 "pending")
    (reservar! ds (nth da-ana 1) (ids "Diego") (dia 10) 2 "pending")
    (reservar! ds (nth da-ana 2) bruno (dia 3) 2 "confirmed")                        ; Ana marca devolvida
    (reservar! ds (nth da-ana 3) (ids "Elisa") (dia -6) 2 "completed" :avaliada? false)))

(defn -main [& _]
  (let [sistema (ig/init (system/config) [:bikerent.listings.adapters.postgres/repository
                                          :bikerent.search.embeddings/provider])
        ds      (:bikerent.db/datasource sistema)
        hoje    (LocalDate/now (java.time.ZoneId/of "America/Sao_Paulo"))]
    (try
      (db/execute-one! ds {:delete-from :users :where [:like :email (str "%" dominio)]})
      (let [ids   (criar-pessoas! ds)
            bikes (criar-bikes! ds ids)]
        (historico! ds bikes ids hoje)
        (roteiro-da-demo! ds bikes ids hoje)
        (println "Pessoas:" (count ids) "| bikes:" (count bikes)
                 "| reservas:" (:n (db/execute-one! ds {:select [[[:count :*] :n]] :from :bookings
                                                        :where [:in :listing_id (map :id bikes)]}))
                 "| avaliações:" (:n (db/execute-one! ds {:select [[[:count :*] :n]] :from :reviews
                                                          :join [:bookings [:= :bookings.id :reviews.booking_id]]
                                                          :where [:in :bookings.listing_id (map :id bikes)]})))
        (println "Vetores gerados:"
                 (search/index-pending! {:listings   (:bikerent.listings.adapters.postgres/repository sistema)
                                         :embeddings (:bikerent.search.embeddings/provider sistema)})))
      (finally
        (ig/halt! sistema)
        (shutdown-agents)))))
