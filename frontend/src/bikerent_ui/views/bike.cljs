(ns bikerent-ui.views.bike
  "Detalhe da bike, seletor de datas, resumo do preço e avaliações."
  (:require [bikerent-ui.views.comum :as c]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

(defn- conflito
  "Período ocupado que cruza o escolhido."
  [ocupados inicio fim]
  (some #(when (and (c/data<= inicio (:fim %)) (c/data<= (:inicio %) fim)) %) ocupados))

(defn- galeria [{:keys [fotos] :as bike}]
  (if (seq fotos)
    [:div {:class "-mx-4 flex snap-x snap-mandatory gap-2 overflow-x-auto px-4 md:mx-0 md:px-0"}
     (for [{:keys [id url]} fotos]
       ^{:key id}
       [:img {:src url :alt (:titulo bike) :class "aspect-[4/3] w-[85%] shrink-0 snap-center rounded-2xl object-cover md:w-[48%]"}])]
    [c/foto-bike bike "aspect-[4/3] w-full rounded-2xl md:aspect-[16/7]"]))

(defn seletor-datas
  "Seletor de datas: retirada e devolução, com os períodos indisponíveis."
  [{:keys [datas ocupados]}]
  (let [{:keys [inicio fim]} @datas]
    [:div
     [:div.grid.grid-cols-2.gap-2
      [c/campo "Retirada" {:type      "date"
                           :min       (c/hoje)
                           :value     (or inicio "")
                           :on-change #(let [v (.. % -target -value)]
                                         (swap! datas (fn [d] (cond-> (assoc d :inicio v)
                                                                (or (str/blank? (:fim d)) (not (c/data<= v (:fim d)))) (assoc :fim v)))))}]
      [c/campo "Devolução" {:type      "date"
                            :min       (or inicio (c/hoje))
                            :value     (or fim "")
                            :on-change #(swap! datas assoc :fim (.. % -target -value))}]]
     (when (seq ocupados)
       [:p.mt-2.text-sm.text-suave
        "Indisponível: " (str/join ", " (map c/periodo-texto ocupados))])]))

(defn- caixa-reserva [{:keys [id diaria_centavos owner_id reservados bloqueios status]}]
  (let [datas (r/atom {})]
    (fn [_]
      (let [user                 @(rf/subscribe [:user])
            enviando?            @(rf/subscribe [:enviando?])
            {:keys [inicio fim]} @datas
            ocupados             (sort-by :inicio (concat reservados bloqueios))
            completo?            (and (seq inicio) (seq fim))
            choque               (when completo? (conflito ocupados inicio fim))
            n                    (when completo? (c/diarias inicio fim))]
        [:div {:class "rounded-2xl border border-borda bg-superficie p-4 md:sticky md:top-20"}
         [:p [:span.text-2xl.font-bold (c/preco diaria_centavos)] [:span.text-suave " / dia"]]
         (cond
           (= owner_id (:id user))
           [:p.mt-3.text-suave "Esta bike é sua. "
            [:a.font-semibold.text-texto.underline {:href (rfe/href :editar-anuncio {:id id})} "Editar anúncio"]]

           (not= "publicado" status)
           [:p.mt-3.text-suave "Esta bike está pausada pelo dono."]

           :else
           [:div.mt-4.space-y-4
            [seletor-datas {:datas datas :ocupados ocupados}]
            (cond
              choque
              [:p.text-sm.text-perigo (str "A bike já está ocupada em " (c/periodo-texto choque) ".")]

              completo?
              [:div.space-y-1.border-t.border-borda.pt-3.text-sm
               [:div.flex.justify-between
                [:span (str (c/preco diaria_centavos) " × " n (if (= 1 n) " diária" " diárias"))]
                [:span (c/preco (* n diaria_centavos))]]
               [:div.flex.justify-between.text-base.font-semibold
                [:span "Total"] [:span (c/preco (* n diaria_centavos))]]])
            [c/botao {:disabled (or (not completo?) choque enviando?)
                      :on-click #(rf/dispatch [:reservar {:listing_id id :inicio inicio :fim fim}])}
             (if enviando? "Enviando..." "Solicitar reserva")]
            [:p.text-center.text-xs.text-suave "Você só paga depois que o dono confirmar."]])]))))

(defn- avaliacoes [lista]
  [:section.mt-8
   [:h2.mb-3.text-lg.font-semibold "Avaliações"]
   (if (seq lista)
     [:ul.space-y-4
      (for [{:keys [id nota comentario autor_nome criado_em]} lista]
        ^{:key id}
        [:li.border-b.border-borda.pb-4
         [:div.flex.items-center.justify-between
          [:span.font-medium autor_nome]
          [c/estrelas {:nota nota}]]
         [:p.text-xs.text-suave (.toLocaleDateString (js/Date. criado_em) "pt-BR")]
         (when (seq comentario) [:p.mt-1 comentario])])]
     [:p.text-suave "Ainda sem avaliações."])])

(defn detalhe []
  [c/conteudo :bike
   (fn [{:keys [titulo cidade tipo aro tamanho descricao dono_nome] :as bike}]
     [:div
      [:a {:href (rfe/href :busca) :class "mb-3 inline-block text-sm text-suave"} "← Voltar à busca"]
      [galeria bike]
      [:div {:class "mt-4 grid gap-6 md:grid-cols-[1fr_340px]"}
       [:div
        [:div.flex.items-start.justify-between.gap-3
         [:h1.text-2xl.font-bold titulo]
         [c/nota-resumo bike]]
        [:p.mt-1.text-suave (str/join " · " (remove nil? [cidade (c/tipo-nome tipo)
                                                          (when aro (str "Aro " aro))
                                                          (when tamanho (str "Tamanho " tamanho))]))]
        [:p.mt-4.text-sm "Anunciada por " [:span.font-semibold dono_nome]]
        (when (seq descricao)
          [:p.mt-4.whitespace-pre-line descricao])
        [c/conteudo :avaliacoes avaliacoes]]
       [:div {:class "order-first md:order-none"}
        ^{:key (:id bike)} [caixa-reserva bike]]]])])
