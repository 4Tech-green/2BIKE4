(ns bikerent.search.embeddings
  "Escolhe o EmbeddingProvider: Gemini se houver GEMINI_API_KEY, senão o fake.
  Os vetores dos dois não são comparáveis: ao trocar, rode
  `update listings set embedding = null` e reinicie para reindexar."
  (:require [bikerent.search.adapters.fake :as fake]
            [bikerent.search.adapters.gemini :as gemini]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defmethod ig/init-key ::provider [_ {:keys [gemini-api-key]}]
  (if (str/blank? gemini-api-key)
    (do (log/info "Busca semântica com vetores fake (defina GEMINI_API_KEY para usar o Gemini)")
        (fake/->FakeEmbeddings))
    (do (log/info "Busca semântica com Gemini")
        (gemini/provider gemini-api-key))))
