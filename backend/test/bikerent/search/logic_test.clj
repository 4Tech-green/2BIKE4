(ns bikerent.search.logic-test
  (:require [bikerent.listings.ports :as listings]
            [bikerent.search.adapters.fake :as fake]
            [bikerent.search.domain :as domain]
            [bikerent.search.logic :as logic]
            [clojure.test :refer [deftest is testing]]))

(defn- similaridade [a b]
  (reduce + (map * (fake/vetor a) (fake/vetor b))))

(deftest vetor-fake
  (testing "acentos, maiúsculas e plurais não atrapalham"
    (is (= ["trilh" "monta"] (fake/tokens "Trilhas na MONTANHA")))
    (is (< 0.99 (similaridade "bicicleta elétrica" "eletricas"))))
  (testing "fica mais perto de quem divide palavras"
    (let [trilha (domain/texto-do-anuncio {:titulo "Caloi Elite" :tipo "mountain" :cidade "Recife"})
          cidade (domain/texto-do-anuncio {:titulo "Monark Barra Circular" :tipo "urbana" :cidade "Recife"})]
      (is (> (similaridade "bike para trilha" trilha)
             (similaridade "bike para trilha" cidade))))))

(defn- repo [anuncios]
  (let [vetores (atom {})]
    {:vetores vetores
     :repo    (reify listings/ListingRepository
                (find-by-id [_ id] (get anuncios id))
                (save-embedding! [_ id v] (swap! vetores assoc id v))
                (ids-without-embedding [_] (remove @vetores (keys anuncios)))
                (search-similar [_ v _]
                  (->> @vetores
                       (map (fn [[id doc]] {:id id :similaridade (reduce + (map * v doc))}))
                       (filter #(pos? (:similaridade %)))
                       (sort-by :similaridade >))))}))

(deftest indexar-e-buscar
  (let [{:keys [repo vetores]} (repo {1 {:id 1 :titulo "Caloi aro 29" :tipo "mountain" :cidade "Recife"}
                                      2 {:id 2 :titulo "Dobrável Dahon" :tipo "dobravel" :cidade "Olinda"}})
        deps {:listings repo :embeddings (fake/->FakeEmbeddings)}]
    (is (= 2 (logic/index-pending! deps)))
    (is (= 0 (logic/index-pending! deps)) "nada pendente na segunda vez")
    (is (= 768 (count (get @vetores 1))))
    (is (= [1] (map :id (logic/search deps "quero fazer trilha"))))
    (is (= [2] (map :id (logic/search deps "compacta pra levar no metrô"))))
    (is (= [] (logic/search deps "quero uma bike")) "sem palavra útil")))
