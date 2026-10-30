(ns bikerent-ui.views.perfil
  (:require [bikerent-ui.config :as config]
            [bikerent-ui.views.comum :as c]
            [re-frame.core :as rf]
            [reitit.frontend.easy :as rfe]))

(defn- trocar-foto [{:keys [foto_url]}]
  (let [enviando (pos? @(rf/subscribe [:fotos-enviando]))]
    [:div.flex.flex-wrap.items-center.gap-3
     (when (config/upload-de-fotos?)
       [:label {:class "cursor-pointer rounded-full border border-primaria px-4 py-2 text-sm font-semibold text-primaria hover:bg-primaria/5"}
        (cond enviando "Enviando..." foto_url "Trocar foto" :else "Adicionar foto")
        [:input {:type      "file"
                 :accept    "image/*"
                 :class     "hidden"
                 :disabled  enviando
                 :on-change (fn [e]
                              (when-let [arquivo (aget (.. e -target -files) 0)]
                                (rf/dispatch [:enviar-foto-perfil arquivo]))
                              (set! (.. e -target -value) ""))}]])
     (when foto_url
       [:button {:type     "button"
                 :class    "text-sm text-suave underline"
                 :on-click #(rf/dispatch [:atualizar-perfil {:foto_url nil}])}
        "Remover foto"])]))

(defn perfil []
  [:div {:class "mx-auto max-w-lg"}
   [c/titulo-pagina "Perfil"]
   [c/conteudo :perfil
    (fn [{:keys [nome email criado_em] :as user}]
      [:div.space-y-4
       [:div.flex.items-center.gap-4.rounded-2xl.border.border-borda.bg-superficie.p-4
        [c/avatar user "h-20 w-20 shrink-0 text-3xl"]
        [:div.min-w-0.space-y-2
         [:div
          [:p.truncate.text-lg.font-semibold nome]
          [:p.truncate.text-sm.text-suave email]
          [:p.text-xs.text-suave (str "Desde " (.toLocaleDateString (js/Date. criado_em) "pt-BR" #js {:month "long" :year "numeric"}))]]
         [trocar-foto user]]]
       [:a {:href  (rfe/href :reservas)
            :class "block rounded-2xl border border-borda bg-superficie p-4 font-medium"}
        "Minhas reservas →"]
       [:a {:href  (rfe/href :meus-anuncios)
            :class "block rounded-2xl border border-borda bg-superficie p-4 font-medium"}
        "Minhas bikes, pedidos e ganhos →"]
       [c/botao {:variante :secundario :on-click #(rf/dispatch [:sair])} "Sair"]])]])
