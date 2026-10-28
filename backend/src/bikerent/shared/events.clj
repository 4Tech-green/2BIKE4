(ns bikerent.shared.events
  "EventPublisher: eventos de domínio num canal core.async (no lugar do Kafka).
  Tipos: :booking-requested :booking-confirmed :booking-rejected :booking-cancelled
  :booking-completed :payment-captured :review-submitted :listing-published."
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]))

(defprotocol EventPublisher
  (publish! [publisher tipo payload] "Publica sem bloquear quem chamou."))

(defrecord AsyncPublisher [ch pub]
  EventPublisher
  (publish! [_ tipo payload]
    (log/info "Evento" tipo payload)
    (a/put! ch {:tipo tipo :payload payload})))

(defn subscribe!
  "Chama `f` com cada evento do `tipo`, numa thread própria. Uma falha em `f`
  só é registrada no log; não derruba a assinatura."
  [{:keys [pub]} tipo f]
  (let [sub (a/chan 64)]
    (a/sub pub tipo sub)
    (a/thread
      (loop []
        (when-some [evento (a/<!! sub)]
          (try (f evento)
               (catch Exception e (log/error e "Falha ao tratar evento" tipo)))
          (recur))))
    sub))

(defmethod ig/init-key ::publisher [_ _]
  (let [ch (a/chan 1024)]
    (->AsyncPublisher ch (a/pub ch :tipo))))

(defmethod ig/halt-key! ::publisher [_ {:keys [ch]}]
  (a/close! ch))
