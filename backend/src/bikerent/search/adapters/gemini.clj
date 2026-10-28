(ns bikerent.search.adapters.gemini
  "Vetores pela API do Gemini (modelo multilíngue, entende sinônimos)."
  (:require [bikerent.search.domain :as domain]
            [bikerent.search.ports :as ports]
            [jsonista.core :as j])
  (:import (java.net URI)
           (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers)
           (java.time Duration)))

(def ^:private url
  "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent")

(defrecord GeminiEmbeddings [api-key ^HttpClient http]
  ports/EmbeddingProvider
  (embed [_ texto uso]
    (let [body (j/write-value-as-string
                {:content              {:parts [{:text texto}]}
                 :taskType             (if (= uso :consulta) "RETRIEVAL_QUERY" "RETRIEVAL_DOCUMENT")
                 :outputDimensionality ports/dimensoes})
          req  (-> (HttpRequest/newBuilder (URI. url))
                   (.header "Content-Type" "application/json")
                   (.header "x-goog-api-key" api-key)
                   (.timeout (Duration/ofSeconds 20))
                   (.POST (HttpRequest$BodyPublishers/ofString body))
                   .build)
          resp (.send http req (HttpResponse$BodyHandlers/ofString))]
      (when-not (= 200 (.statusCode resp))
        (throw (ex-info "Gemini respondeu com erro" {:status (.statusCode resp) :body (.body resp)})))
      ;; com menos de 3072 dimensões o Gemini não normaliza o vetor
      (domain/normalizar
       (get-in (j/read-value (.body resp) j/keyword-keys-object-mapper) [:embedding :values])))))

(defn provider [api-key]
  (->GeminiEmbeddings api-key (HttpClient/newHttpClient)))
