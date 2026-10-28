(ns bikerent.search.ports)

(def dimensoes
  "Tamanho da coluna listings.embedding."
  768)

(defprotocol EmbeddingProvider
  (embed [provider texto uso] "Vetor normalizado de `dimensoes` números. `uso` é :documento ou :consulta."))
