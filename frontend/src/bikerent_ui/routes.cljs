(ns bikerent-ui.routes
  "Rotas com hash (#/...), que funcionam igual na web, no Capacitor e no Electron.
  `:carregar` diz o que buscar na API ao entrar na tela; `:login?` exige sessão."
  (:require [re-frame.core :as rf]
            [reitit.frontend :as reitit]
            [reitit.frontend.easy :as rfe]))

(defn- minhas-reservas [_]
  [[:carregar :minhas-reservas {:path "/bookings/mine"}]])

(def rotas
  [["/"           {:name     :inicio
                   :carregar (fn [_] [[:carregar :recentes {:path "/listings"}]])}]
   ["/entrar"     {:name :entrar}]
   ["/cadastro"   {:name :cadastro}]
   ["/busca"      {:name     :busca
                   :carregar (fn [{:keys [query]}] [[:buscar query]])}]
   ["/bikes/:id"  {:name     :bike
                   :carregar (fn [{{:keys [id]} :path}]
                               [[:carregar :bike {:path (str "/listings/" id)}]
                                [:carregar :avaliacoes {:path (str "/listings/" id "/reviews")}]])}]
   ["/reservas"   {:name :reservas :login? true :carregar minhas-reservas}]
   ["/reservas/:id"         {:name :reserva :login? true :carregar minhas-reservas}]
   ["/reservas/:id/pagar"   {:name :pagar :login? true :carregar minhas-reservas}]
   ["/reservas/:id/avaliar" {:name :avaliar :login? true :carregar minhas-reservas}]
   ["/perfil"     {:name     :perfil
                   :login?   true
                   :carregar (fn [_] [[:carregar :perfil {:path "/me"}]])}]])

(def router (reitit/router rotas))

(defn iniciar! []
  (rfe/start! router #(rf/dispatch [:rota-mudou %]) {:use-fragment true}))
