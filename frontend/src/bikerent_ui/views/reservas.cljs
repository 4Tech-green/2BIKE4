(ns bikerent-ui.views.reservas
  "Minhas reservas, detalhe/confirmação, pagamento simulado e avaliação."
  (:require [bikerent-ui.views.comum :as c]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

(defn- paga? [{:keys [pagamento]}] (= "captured" pagamento))

(defn- pode-avaliar? [{:keys [status ja_avaliei]}]
  (and (= "completed" status)
       (not (every? (set ja_avaliei) ["listing" "owner"]))))

(defn- resumo [{:keys [titulo cidade inicio fim total_centavos] :as reserva}]
  [:div.flex.gap-3
   [c/foto-bike reserva "h-20 w-24 shrink-0 rounded-xl"]
   [:div.min-w-0.flex-1
    [:p.truncate.font-semibold titulo]
    [:p.text-sm.text-suave cidade]
    [:p.text-sm (c/periodo-texto {:inicio inicio :fim fim}) " · " (c/preco total_centavos)]]])

(defn lista []
  [:div
   [c/titulo-pagina "Minhas reservas"]
   [c/conteudo :minhas-reservas
    (fn [reservas]
      (if (empty? reservas)
        [c/vazio "Você ainda não fez nenhuma reserva."
         [:a {:href (rfe/href :busca) :class "font-semibold underline"} "Encontrar uma bike"]]
        [:ul.space-y-3
         (for [{:keys [id] :as r} reservas]
           ^{:key id}
           [:li
            [:a {:href  (rfe/href :reserva {:id id})
                 :class "block rounded-2xl border border-borda bg-superficie p-3 hover:shadow-md"}
             [resumo r]
             [:div.mt-3.flex.items-center.justify-between
              [c/badge-status r]
              (cond
                (and (= "confirmed" (:status r)) (not (paga? r)))
                [:span.text-sm.font-semibold.text-info "Pagar agora →"]

                (pode-avaliar? r)
                [:span.text-sm.font-semibold "Avaliar →"])]]])]))]])

(def ^:private proximo-passo
  {"pending"   "O dono da bike vai confirmar ou recusar seu pedido. Você só paga depois da confirmação."
   "confirmed" "O dono confirmou! Faça o pagamento para garantir a bike."
   "paga"      "Pagamento feito. Combine a retirada com o dono; depois da devolução ele marca a reserva como concluída."
   "completed" "Reserva concluída. Conte como foi!"
   "rejected"  "O dono não pôde atender este pedido. Que tal procurar outra bike?"
   "cancelled" "Esta reserva foi cancelada."})

(defn- com-reserva
  "Acha a reserva da rota em /bookings/mine e chama `render` com ela."
  [id render]
  [c/conteudo :minhas-reservas
   (fn [_]
     (if-let [reserva @(rf/subscribe [:minha-reserva id])]
       (render reserva)
       [c/vazio "Reserva não encontrada."]))])

(defn detalhe [id]
  [com-reserva id
   (fn [{:keys [status listing_id dono_nome pagamento] :as reserva}]
     (let [enviando? @(rf/subscribe [:enviando?])
           etapa     (if (and (= "confirmed" status) (paga? reserva)) "paga" status)]
       [:div {:class "mx-auto max-w-lg"}
        [c/titulo-pagina "Reserva" (rfe/href :reservas)]
        [:div.space-y-4.rounded-2xl.border.border-borda.bg-superficie.p-4
         [resumo reserva]
         [:div.flex.items-center.justify-between
          [c/badge-status reserva]
          [:a.text-sm.underline {:href (rfe/href :bike {:id listing_id})} "Ver bike"]]
         [:p.text-sm (proximo-passo etapa)]
         [:p.text-sm.text-suave "Dono: " dono_nome]
         (when (= "refunded" pagamento)
           [:p.text-sm.text-suave "O valor pago foi estornado."])]
        [:div.mt-4.space-y-2
         (when (= "confirmed" etapa)
           [:a {:href (rfe/href :pagar {:id id})
                :class "block rounded-xl bg-primaria px-4 py-3 text-center font-semibold text-primaria-contraste"}
            "Pagar " (c/preco (:total_centavos reserva))])
         (when (pode-avaliar? reserva)
           [:a {:href (rfe/href :avaliar {:id id})
                :class "block rounded-xl bg-primaria px-4 py-3 text-center font-semibold text-primaria-contraste"}
            "Avaliar"])
         (when (#{"pending" "confirmed"} status)
           [c/botao {:variante :perigo
                     :disabled enviando?
                     :on-click #(when (js/confirm (if (paga? reserva)
                                                    "Cancelar a reserva? O valor pago será estornado."
                                                    "Cancelar a reserva?"))
                                  (rf/dispatch [:cancelar id]))}
            "Cancelar reserva"])]]))])

(defn pagamento [id]
  [com-reserva id
   (fn [{:keys [status total_centavos inicio fim] :as reserva}]
     (let [enviando? @(rf/subscribe [:enviando?])]
       [:div {:class "mx-auto max-w-lg"}
        [c/titulo-pagina "Pagamento" (rfe/href :reserva {:id id})]
        [:div.rounded-2xl.border.border-borda.bg-superficie.p-4
         [resumo reserva]
         [:div.mt-4.space-y-1.border-t.border-borda.pt-3.text-sm
          [:div.flex.justify-between
           [:span (str (c/diarias inicio fim) " diária(s)")] [:span (c/preco total_centavos)]]
          [:div.flex.justify-between.text-base.font-semibold
           [:span "Total"] [:span (c/preco total_centavos)]]]]
        (if (or (not= "confirmed" status) (paga? reserva))
          [:p.mt-4.text-suave "Esta reserva não está aguardando pagamento."]
          [:form.mt-4.space-y-3 {:on-submit (fn [e] (.preventDefault e) (rf/dispatch [:pagar id]))}
           [:div {:class "rounded-xl border border-info/30 bg-info/5 p-3 text-sm text-info"}
            "Pagamento simulado: nenhum valor é cobrado de verdade."]
           [c/campo "Nome no cartão" {:default-value "Fulano de Tal" :required true}]
           [c/campo "Número do cartão" {:default-value "4242 4242 4242 4242" :inputmode "numeric" :required true}]
           [:div.grid.grid-cols-2.gap-2
            [c/campo "Validade" {:default-value "12/30" :required true}]
            [c/campo "CVV" {:default-value "123" :inputmode "numeric" :required true}]]
           [c/botao {:type "submit" :disabled enviando?}
            (if enviando? "Processando..." (str "Pagar " (c/preco total_centavos)))]])]))])

(defn- bloco-nota [titulo nota]
  [:div.rounded-2xl.border.border-borda.bg-superficie.p-4
   [:p.mb-2.font-semibold titulo]
   [c/estrelas {:nota @nota :ao-escolher #(reset! nota %) :tamanho :grande}]])

(defn avaliacao [id]
  (let [nota-bike  (r/atom 0)
        nota-dono  (r/atom 0)
        comentario (r/atom "")]
    (fn [id]
      [com-reserva id
       (fn [{:keys [ja_avaliei dono_nome] :as reserva}]
         (let [feitas    (set ja_avaliei)
               enviando? @(rf/subscribe [:enviando?])
               pendentes (cond-> {}
                           (not (feitas "listing")) (assoc "listing" {:nota @nota-bike :comentario @comentario})
                           (not (feitas "owner"))   (assoc "owner" {:nota @nota-dono}))]
           [:div {:class "mx-auto max-w-lg"}
            [c/titulo-pagina "Avaliar" (rfe/href :reserva {:id id})]
            [resumo reserva]
            (if (empty? pendentes)
              [:p.mt-4.text-suave "Você já avaliou esta reserva. Obrigado!"]
              [:form.mt-4.space-y-4
               {:on-submit (fn [e]
                             (.preventDefault e)
                             (if (some (comp zero? :nota) (vals pendentes))
                               (rf/dispatch [:avisar :erro "Escolha de 1 a 5 estrelas"])
                               (rf/dispatch [:avaliar id pendentes])))}
               (when (contains? pendentes "listing")
                 [:<>
                  [bloco-nota "Como estava a bike?" nota-bike]
                  [:label.block
                   [:span.mb-1.block.text-sm.font-medium "Comentário (opcional)"]
                   [:textarea {:value     @comentario
                               :on-change #(reset! comentario (.. % -target -value))
                               :max-length 1000
                               :rows      3
                               :class     "w-full rounded-xl border border-borda bg-superficie p-3 outline-none focus:border-primaria"}]]])
               (when (contains? pendentes "owner")
                 [bloco-nota (str "Como foi negociar com " dono_nome "?") nota-dono])
               [c/botao {:type "submit" :disabled enviando?}
                (if enviando? "Enviando..." "Enviar avaliação")]])]))])))
