(ns bikerent.search.logic
  "Busca semântica. `deps` traz {:listings ListingRepository :embeddings EmbeddingProvider}."
  (:require [bikerent.listings.ports :as listings]
            [bikerent.search.domain :as domain]
            [bikerent.search.ports :as ports]))

(def ^:private limite 20)

(defn index!
  "Gera e guarda o vetor do anúncio."
  [{:keys [listings embeddings]} listing-id]
  (when-let [listing (listings/find-by-id listings listing-id)]
    (listings/save-embedding! listings listing-id
                              (ports/embed embeddings (domain/texto-do-anuncio listing) :documento))))

(defn index-pending!
  "Indexa os anúncios que ainda não têm vetor; devolve quantos foram."
  [{:keys [listings] :as deps}]
  (let [ids (listings/ids-without-embedding listings)]
    (run! #(index! deps %) ids)
    (count ids)))

(defn search [{:keys [listings embeddings]} q]
  (let [vetor (ports/embed embeddings q :consulta)]
    ;; consulta sem nenhuma palavra útil (só no fake): não há o que comparar
    (if (every? zero? vetor)
      []
      (listings/search-similar listings vetor limite))))
