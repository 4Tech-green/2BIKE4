(ns bikerent.bookings.domain
  "Regras da reserva: total, quem pode o quê e as transições de estado
  pending → confirmed → completed, com saídas para rejected e cancelled."
  (:require [bikerent.users.domain :as users])
  (:import (java.time.temporal ChronoUnit)))

(defn diarias
  "Datas inclusivas: de 10 a 12 são 3 diárias."
  [{:keys [inicio fim]}]
  (inc (.between ChronoUnit/DAYS inicio fim)))

(defn total-centavos [periodo diaria-centavos]
  (* (diarias periodo) diaria-centavos))

(defn dono? [booking user]
  (= (:owner_id booking) (:id user)))

(defn locatario? [booking user]
  (= (:renter_id booking) (:id user)))

(defn pode-gerir?
  "Dono da bike ou admin."
  [booking user]
  (or (dono? booking user) (users/admin? user)))

(defn pode-ver? [booking user]
  (or (locatario? booking user) (pode-gerir? booking user)))

(defn pago? [booking]
  (= "captured" (:pagamento booking)))

(def acoes
  {:confirm  {:de #{"pending"}             :para "confirmed" :quem pode-gerir?  :verbo "confirmar"}
   :reject   {:de #{"pending"}             :para "rejected"  :quem pode-gerir?  :verbo "recusar"}
   :cancel   {:de #{"pending" "confirmed"} :para "cancelled" :quem pode-ver?    :verbo "cancelar"}
   :pay      {:de #{"confirmed"}                             :quem locatario?   :verbo "pagar"}
   :complete {:de #{"confirmed"}           :para "completed" :quem pode-gerir?  :verbo "concluir"}})

(defn erro-da-acao
  "nil se `user` pode aplicar `acao` à reserva; senão [tipo-do-erro mensagem]."
  [acao booking user]
  (let [{:keys [de quem verbo]} (acoes acao)]
    (cond
      (not (quem booking user))
      [:forbidden (str "Você não pode " verbo " esta reserva")]

      (not (de (:status booking)))
      [:conflict (str "Não dá para " verbo " uma reserva com status " (:status booking))]

      (and (= acao :pay) (pago? booking))
      [:conflict "Esta reserva já foi paga"]

      (and (= acao :complete) (not (pago? booking)))
      [:conflict "A reserva só pode ser concluída depois do pagamento"])))
