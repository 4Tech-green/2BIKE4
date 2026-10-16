(ns bikerent.shared.time
  (:require [bikerent.shared.errors :as errors])
  (:import (java.time LocalDate)
           (java.time.format DateTimeParseException)))

(defn parse-date [s]
  (try
    (LocalDate/parse s)
    (catch DateTimeParseException _
      (errors/error! :bad-request (str "Data inválida: " s)))))

(defn periodo
  "Período com as duas datas inclusivas: de 10 a 12 são 3 diárias."
  [inicio fim]
  (let [i (parse-date inicio)
        f (parse-date fim)]
    (when (.isBefore f i)
      (errors/error! :bad-request "A data final deve ser igual ou posterior à inicial"))
    {:inicio i :fim f}))
