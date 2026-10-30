(ns bikerent-ui.routes
  "Rotas com hash (#/...), que funcionam igual na web, no Capacitor e no Electron.
  `:carregar` diz o que buscar na API ao entrar na tela; `:login?` exige sessão."
  (:require [re-frame.core :as rf]
            [reitit.frontend :as reitit]
            [reitit.frontend.easy :as rfe]))

(defn- minhas-reservas [_]
  [[:carregar :minhas-reservas {:path "/bookings/mine"}]])

(defn- meus-anuncios [_]
  [[:carregar :meus-anuncios {:path "/me/listings"}]])

(defn- pedidos [_]
  [[:carregar :pedidos {:path "/bookings/received"}]])

(defn- hoje []
  (let [d    (js/Date.)
        dois #(.padStart (str %) 2 "0")]
    (str (.getFullYear d) "-" (dois (inc (.getMonth d))) "-" (dois (.getDate d)))))

(def rotas
  [["/"           {:name     :inicio
                   :carregar (fn [_]
                               [[:carregar :disponiveis-hoje {:path "/listings" :query {:inicio (hoje) :fim (hoje)}}]
                                [:localizar-auto]])}]
   ["/entrar"     {:name :entrar}]
   ["/cadastro"   {:name :cadastro}]
   ["/busca"      {:name     :busca
                   :carregar (fn [{:keys [query]}] [[:buscar query] [:localizar-auto]])}]
   ["/bikes/:id"  {:name     :bike
                   :carregar (fn [{{:keys [id]} :path}]
                               [[:carregar :bike {:path (str "/listings/" id)}]
                                [:carregar :avaliacoes {:path (str "/listings/" id "/reviews")}]])}]
   ["/reservas"   {:name :reservas :login? true :carregar minhas-reservas}]
   ["/reservas/:id"         {:name :reserva :login? true :carregar minhas-reservas}]
   ["/reservas/:id/pagar"   {:name :pagar :login? true :carregar minhas-reservas}]
   ["/reservas/:id/avaliar" {:name :avaliar :login? true :carregar minhas-reservas}]
   ;; proprietário
   ["/anuncios"   {:name :meus-anuncios :login? true :carregar meus-anuncios}]
   ;; "novo" também casa com :id; :conflicting faz testar na ordem
   ["/anuncios/novo" {:name        :novo-anuncio
                      :login?      true
                      :conflicting true
                      :carregar    (fn [_] [[:limpar-rascunho]])}]
   ["/anuncios/:id"  {:name        :editar-anuncio
                      :conflicting true
                      :login?      true
                      :carregar (fn [{{:keys [id]} :path}]
                                  [[:carregar :anuncio {:path (str "/listings/" id)}]])}]
   ["/pedidos"    {:name :pedidos :login? true :carregar pedidos}]
   ["/ganhos"     {:name :ganhos :login? true :carregar pedidos}]
   ["/perfil"     {:name     :perfil
                   :login?   true
                   :carregar (fn [_] [[:carregar :perfil {:path "/me"}]])}]])

(def router (reitit/router rotas))

(defn iniciar! []
  (rfe/start! router #(rf/dispatch [:rota-mudou %]) {:use-fragment true}))
