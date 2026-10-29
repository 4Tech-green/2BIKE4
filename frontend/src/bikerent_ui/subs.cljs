(ns bikerent-ui.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub :rota (fn [db _] (:rota db)))
(rf/reg-sub :user (fn [db _] (:user db)))
(rf/reg-sub :logado? (fn [db _] (some? (:token db))))
(rf/reg-sub :enviando? (fn [db _] (:enviando? db)))
(rf/reg-sub :aviso (fn [db _] (:aviso db)))
(rf/reg-sub :local (fn [db _] (:local db)))
(rf/reg-sub :localizando? (fn [db _] (:localizando? db)))
(rf/reg-sub :bike-selecionada (fn [db _] (:bike-selecionada db)))

(rf/reg-sub :dados (fn [db [_ chave]] (get-in db [:dados chave])))
(rf/reg-sub :carregando? (fn [db [_ chave]] (get-in db [:carregando chave])))
(rf/reg-sub :erro (fn [db [_ chave]] (get-in db [:erros chave])))

(rf/reg-sub
 :minha-reserva
 :<- [:dados :minhas-reservas]
 (fn [reservas [_ id]]
   (some #(when (= (str (:id %)) (str id)) %) reservas)))
