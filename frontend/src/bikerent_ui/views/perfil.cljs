(ns bikerent-ui.views.perfil
  (:require [bikerent-ui.views.comum :as c]
            [re-frame.core :as rf]
            [reitit.frontend.easy :as rfe]))

(defn perfil []
  [:div {:class "mx-auto max-w-lg"}
   [c/titulo-pagina "Perfil"]
   [c/conteudo :perfil
    (fn [{:keys [nome email criado_em]}]
      [:div.space-y-4
       [:div.flex.items-center.gap-4.rounded-2xl.border.border-borda.bg-superficie.p-4
        [:div {:class "flex h-14 w-14 items-center justify-center rounded-full bg-primaria text-xl font-bold text-primaria-contraste"}
         (first nome)]
        [:div
         [:p.text-lg.font-semibold nome]
         [:p.text-sm.text-suave email]
         [:p.text-xs.text-suave (str "Desde " (.toLocaleDateString (js/Date. criado_em) "pt-BR" #js {:month "long" :year "numeric"}))]]]
       [:a {:href  (rfe/href :reservas)
            :class "block rounded-2xl border border-borda bg-superficie p-4 font-medium"}
        "Minhas reservas →"]
       [:a {:href  (rfe/href :meus-anuncios)
            :class "block rounded-2xl border border-borda bg-superficie p-4 font-medium"}
        "Minhas bikes, pedidos e ganhos →"]
       [c/botao {:variante :secundario :on-click #(rf/dispatch [:sair])} "Sair"]])]])
