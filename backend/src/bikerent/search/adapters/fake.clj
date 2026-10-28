(ns bikerent.search.adapters.fake
  "Vetores sem IA: cada palavra (sem acento, cortada nas 5 primeiras letras para
  juntar plurais e flexões) soma 1 numa posição escolhida pelo hash. Só acha
  palavras em comum, mas roda offline e de graça, e serve para dev e testes."
  (:require [bikerent.search.domain :as domain]
            [bikerent.search.ports :as ports]
            [clojure.string :as str])
  (:import (java.text Normalizer Normalizer$Form)))

(def ^:private ignoradas
  #{"para" "com" "uma" "que" "por" "sem" "nas" "nos" "das" "dos" "bike" "bikes"
    "bicicleta" "bicicletas" "quero" "preciso" "procuro" "algo" "alguma"})

(defn tokens [texto]
  (->> (str/split (-> (Normalizer/normalize (str texto) Normalizer$Form/NFD)
                      (str/replace #"\p{M}" "")
                      str/lower-case)
                  #"[^a-z0-9]+")
       (remove #(< (count %) 3))
       (remove ignoradas)
       (map #(subs % 0 (min 5 (count %))))))

(defn vetor [texto]
  (domain/normalizar
   (reduce (fn [v t] (update v (mod (hash t) ports/dimensoes) inc))
           (vec (repeat ports/dimensoes 0.0))
           (tokens texto))))

(defrecord FakeEmbeddings []
  ports/EmbeddingProvider
  (embed [_ texto _] (vetor texto)))
