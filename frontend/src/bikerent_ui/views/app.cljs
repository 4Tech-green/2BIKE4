(ns bikerent-ui.views.app
  (:require [bikerent-ui.views.bike :as bike]
            [bikerent-ui.views.busca :as busca]
            [bikerent-ui.views.comum :as c]
            [bikerent-ui.views.entrada :as entrada]
            [bikerent-ui.views.perfil :as perfil]
            [bikerent-ui.views.reservas :as reservas]
            [re-frame.core :as rf]))

(defn- pagina [{:keys [data path-params query-params]}]
  (let [id (:id path-params)]
    (case (:name data)
      :inicio   [busca/inicio]
      :entrar   [entrada/login]
      :cadastro [entrada/cadastro]
      ;; nova busca recria a tela, para o campo mostrar o texto da URL
      :busca    ^{:key (:q query-params)} [busca/resultados query-params]
      :bike     [bike/detalhe]
      :reservas [reservas/lista]
      :reserva  [reservas/detalhe id]
      :pagar    [reservas/pagamento id]
      :avaliar  ^{:key id} [reservas/avaliacao id]
      :perfil   [perfil/perfil]
      [c/carregando])))

(defn app []
  (let [rota  @(rf/subscribe [:rota])
        atual (get-in rota [:data :name])]
    [:div {:class "min-h-screen pb-24 md:pb-10"}
     [c/aviso]
     [c/cabecalho atual]
     [:main {:class "mx-auto max-w-5xl px-4 pt-4"}
      [pagina rota]]
     [c/barra-navegacao atual]]))
