(ns bikerent.search.indexer
  "Assina :listing-published e mantém os vetores dos anúncios em dia. Ao subir,
  também indexa o que estiver sem vetor (anúncios do seed, por exemplo)."
  (:require [bikerent.search.logic :as logic]
            [bikerent.shared.events :as events]
            [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defmethod ig/init-key ::indexer [_ {:keys [events] :as deps}]
  (a/thread
    (try
      (log/info "Anúncios indexados na partida:" (logic/index-pending! deps))
      (catch Exception e (log/error e "Falha ao indexar anúncios pendentes"))))
  (events/subscribe! events :listing-published
                     #(logic/index! deps (get-in % [:payload :listing_id]))))

(defmethod ig/halt-key! ::indexer [_ sub]
  (a/close! sub))
