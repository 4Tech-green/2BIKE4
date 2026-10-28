(ns bikerent-ui.views.dono
  "Telas do proprietário: anúncios, criar/editar com fotos e disponibilidade,
  pedidos recebidos e ganhos."
  (:require [bikerent-ui.config :as config]
            [bikerent-ui.views.bike :as bike]
            [bikerent-ui.views.calendario :refer [calendario]]
            [bikerent-ui.views.comum :as c]
            [bikerent-ui.views.mapa :as mapa]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

(defn- abas [atual]
  [:div {:class "mb-4 flex gap-1 rounded-xl border border-borda bg-superficie p-1 text-sm"}
   (for [[nome texto] [[:meus-anuncios "Anúncios"] [:pedidos "Pedidos"] [:ganhos "Ganhos"]]]
     ^{:key nome}
     [:a {:href  (rfe/href nome)
          :class (str "flex-1 rounded-lg px-3 py-2 text-center font-medium "
                      (if (= nome atual) "bg-primaria text-primaria-contraste" "text-suave"))}
      texto])])

(defn- badge-anuncio [status]
  (if (= "publicado" status)
    [:span {:class "rounded-full bg-sucesso/10 px-3 py-1 text-xs font-semibold text-sucesso"} "Publicado"]
    [:span {:class "rounded-full bg-borda px-3 py-1 text-xs font-semibold text-suave"} "Pausado"]))

;; ---------------------------------------------------------------- meus anúncios

(defn meus-anuncios []
  [:div
   [abas :meus-anuncios]
   [:a {:href  (rfe/href :novo-anuncio)
        :class "mb-4 block rounded-xl bg-primaria px-4 py-3 text-center font-semibold text-primaria-contraste"}
    "+ Anunciar uma bike"]
   [c/conteudo :meus-anuncios
    (fn [anuncios]
      (if (empty? anuncios)
        [c/vazio "Você ainda não anunciou nenhuma bike. Que tal ganhar uma renda extra com a sua?"]
        [:ul.space-y-3
         (for [{:keys [id titulo cidade diaria_centavos status] :as a} anuncios]
           ^{:key id}
           [:li
            [:a {:href  (rfe/href :editar-anuncio {:id id})
                 :class "flex gap-3 rounded-2xl border border-borda bg-superficie p-3 hover:shadow-md"}
             [c/foto-bike a "h-20 w-24 shrink-0 rounded-xl"]
             [:div.min-w-0.flex-1
              [:div.flex.items-start.justify-between.gap-2
               [:p.truncate.font-semibold titulo]
               [badge-anuncio status]]
              [:p.text-sm.text-suave cidade]
              [:div.mt-1.flex.items-center.justify-between
               [:span.text-sm (c/preco diaria_centavos) " / dia"]
               [c/nota-resumo a]]]]])]))]])

;; ---------------------------------------------------------------- criar / editar

(defn- do-anuncio [{:keys [titulo descricao tipo aro tamanho diaria_centavos cidade lat lng]}]
  {:titulo    (or titulo "")
   :descricao (or descricao "")
   :tipo      (or tipo "urbana")
   :aro       (if aro (str aro) "")
   :tamanho   (or tamanho "")
   :diaria    (if diaria_centavos (str (/ diaria_centavos 100)) "")
   :cidade    (or cidade "")
   :lat       lat
   :lng       lng})

(defn- para-api [{:keys [titulo descricao tipo aro tamanho diaria cidade lat lng]}]
  (cond-> {:titulo          (str/trim titulo)
           :descricao       (str/trim descricao)
           :tipo            tipo
           :cidade          (str/trim cidade)
           :diaria_centavos (js/Math.round (* 100 (js/parseFloat diaria)))}
    (seq aro)     (assoc :aro (js/parseInt aro))
    (seq tamanho) (assoc :tamanho tamanho)
    lat           (assoc :lat lat :lng lng)))

(defn- usar-minha-localizacao [form]
  (if-let [geo (some-> js/navigator .-geolocation)]
    (.getCurrentPosition geo
                         #(swap! form assoc :lat (.. % -coords -latitude) :lng (.. % -coords -longitude))
                         #(rf/dispatch [:avisar :erro "Não foi possível obter sua localização"]))
    (rf/dispatch [:avisar :erro "Seu navegador não informa a localização"])))

(defn- form-anuncio [anuncio]
  (let [form (r/atom (do-anuncio anuncio))]
    (fn [anuncio]
      (let [enviando? @(rf/subscribe [:enviando?])
            campo     (fn [k] {:value (get @form k) :on-change #(swap! form assoc k (.. % -target -value))})
            selecao   (fn [rotulo k opcoes]
                        [:label.block
                         [:span.mb-1.block.text-sm.font-medium rotulo]
                         (into [:select (merge {:class "w-full rounded-xl border border-borda bg-superficie px-3 py-3"} (campo k))]
                               (for [[v nome] opcoes] [:option {:key v :value v} nome]))])]
        [:form.space-y-4
         {:on-submit (fn [e]
                       (.preventDefault e)
                       (rf/dispatch [:salvar-anuncio (:id anuncio) (para-api @form)]))}
         [c/campo "Título" (merge {:required true :min-length 3 :max-length 120
                                   :placeholder "Ex.: Caloi Elite aro 29"} (campo :titulo))]
         [:div.grid.grid-cols-2.gap-3
          (selecao "Tipo" :tipo c/tipos)
          [c/campo "Diária (R$)" (merge {:type "number" :required true :min 1 :step "0.01" :inputmode "decimal"} (campo :diaria))]
          [c/campo "Aro (opcional)" (merge {:type "number" :min 12 :max 29} (campo :aro))]
          (selecao "Tamanho do quadro" :tamanho (into [["" "Não informar"]] (map (juxt identity identity) ["PP" "P" "M" "G" "GG"])))]
         [:label.block
          [:span.mb-1.block.text-sm.font-medium "Descrição"]
          [:textarea (merge {:rows 4 :max-length 2000
                             :placeholder "Estado da bike, acessórios, onde retirar..."
                             :class "w-full rounded-xl border border-borda bg-superficie p-3 outline-none focus:border-primaria"}
                            (campo :descricao))]]
         [c/campo "Cidade" (merge {:required true :min-length 2 :max-length 100} (campo :cidade))]
         [:div
          [:div.mb-1.flex.items-center.justify-between
           [:span.text-sm.font-medium "Onde a bike fica (toque no mapa)"]
           [:button {:type "button" :class "text-sm underline" :on-click #(usar-minha-localizacao form)}
            "Usar minha localização"]]
          [mapa/seletor-local [(:lat @form) (:lng @form)] (fn [[lat lng]] (swap! form assoc :lat lat :lng lng))]]
         [c/botao {:type "submit" :disabled enviando?}
          (cond enviando? "Salvando..." (:id anuncio) "Salvar alterações" :else "Publicar anúncio")]]))))

(defn- secao [titulo & filhos]
  (into [:section.mt-8 [:h2.mb-3.text-lg.font-semibold titulo]] filhos))

(defn- fotos [_]
  (let [url (r/atom "")]
    (fn [{:keys [id fotos]}]
      (let [enviando? @(rf/subscribe [:enviando?])]
        [:div
         (when (seq fotos)
           [:div {:class "mb-3 grid grid-cols-3 gap-2"}
            (for [{foto-id :id foto-url :url} fotos]
              ^{:key foto-id}
              [:div.relative
               [:img {:src foto-url :alt "" :class "aspect-square w-full rounded-xl object-cover"}]
               [:button {:type     "button"
                         :on-click #(rf/dispatch [:remover-foto id foto-id])
                         :class    "absolute right-1 top-1 h-7 w-7 rounded-full bg-black/60 text-white"
                         :aria-label "Remover foto"}
                "×"]])])
         (if (config/upload-de-fotos?)
           [:label {:class "block cursor-pointer rounded-xl border-2 border-dashed border-borda p-4 text-center text-suave"}
            (if enviando? "Enviando..." "+ Adicionar foto")
            [:input {:type      "file"
                     :accept    "image/*"
                     :class     "hidden"
                     :disabled  enviando?
                     :on-change #(when-let [arquivo (aget (.. % -target -files) 0)]
                                   (rf/dispatch [:enviar-foto id arquivo])
                                   (set! (.. % -target -value) ""))}]]
           [:form.flex.gap-2
            {:on-submit (fn [e]
                          (.preventDefault e)
                          (rf/dispatch [:adicionar-foto id (str/trim @url)])
                          (reset! url ""))}
            [:input {:type        "url"
                     :required    true
                     :value       @url
                     :on-change   #(reset! url (.. % -target -value))
                     :placeholder "Cole o link de uma foto (https://...)"
                     :class       "min-w-0 flex-1 rounded-xl border border-borda bg-superficie px-3 py-3"}]
            [:button {:type "submit" :disabled enviando?
                      :class "rounded-xl bg-primaria px-4 font-semibold text-primaria-contraste"}
             "Adicionar"]])]))))

(defn- disponibilidade [_]
  (let [datas (r/atom {})]
    (fn [{:keys [id reservados bloqueios] :as anuncio}]
      (let [{:keys [inicio fim]} @datas
            enviando? @(rf/subscribe [:enviando?])]
        [:div.space-y-4
         [calendario anuncio]
         [:div.rounded-2xl.border.border-borda.bg-superficie.p-4
          [:p.mb-3.text-sm.text-suave "Vai usar a bike ou viajar? Bloqueie as datas para ninguém reservar."]
          [bike/seletor-datas {:datas datas :ocupados reservados}]
          [:div.mt-3
           [c/botao {:variante :secundario
                     :disabled (or (empty? inicio) (empty? fim) enviando?)
                     :on-click #(do (rf/dispatch [:bloquear-datas id @datas]) (reset! datas {}))}
            "Bloquear datas"]]]
         (when (seq bloqueios)
           [:ul.space-y-2
            (for [{bloqueio-id :id :as b} bloqueios]
              ^{:key bloqueio-id}
              [:li.flex.items-center.justify-between.rounded-xl.border.border-borda.bg-superficie.px-4.py-2
               [:span (str "Bloqueado: " (c/periodo-texto b))]
               [:button {:type "button" :class "text-sm font-semibold underline"
                         :on-click #(rf/dispatch [:desbloquear-datas id bloqueio-id])}
                "Liberar"]])])]))))

(defn novo-anuncio []
  [:div {:class "mx-auto max-w-lg"}
   [c/titulo-pagina "Anunciar bike" (rfe/href :meus-anuncios)]
   [form-anuncio nil]])

(defn editar-anuncio []
  [c/conteudo :anuncio
   (fn [{:keys [id status owner_id] :as anuncio}]
     (let [user      @(rf/subscribe [:user])
           enviando? @(rf/subscribe [:enviando?])]
       (if (not= owner_id (:id user))
         [c/vazio "Este anúncio não é seu."]
         [:div {:class "mx-auto max-w-lg"}
          [c/titulo-pagina "Editar anúncio" (rfe/href :meus-anuncios)]
          [:div.mb-6.flex.items-center.justify-between.gap-3.rounded-2xl.border.border-borda.bg-superficie.p-4
           [:div.flex.items-center.gap-3
            [badge-anuncio status]
            [:a.text-sm.underline {:href (rfe/href :bike {:id id})} "Ver como locatário"]]
           [:button {:type     "button"
                     :disabled enviando?
                     :class    "rounded-lg border border-borda px-3 py-2 text-sm font-semibold"
                     :on-click #(rf/dispatch [:mudar-status-anuncio id (if (= "publicado" status) "pausado" "publicado")])}
            (if (= "publicado" status) "Pausar" "Publicar")]]
          ^{:key id} [form-anuncio anuncio]
          [secao "Fotos" ^{:key id} [fotos anuncio]]
          [secao "Disponibilidade" ^{:key id} [disponibilidade anuncio]]])))])

;; ---------------------------------------------------------------- pedidos recebidos

(defn- avaliar-locatario [id]
  (let [nota (r/atom 0)]
    (fn [id]
      [:div.mt-3.flex.items-center.justify-between.gap-2.border-t.border-borda.pt-3
       [:div
        [:p.text-sm.font-medium "Como foi o locatário?"]
        [c/estrelas {:nota @nota :ao-escolher #(reset! nota %)}]]
       [:button {:type     "button"
                 :disabled (zero? @nota)
                 :class    "rounded-lg bg-primaria px-3 py-2 text-sm font-semibold text-primaria-contraste disabled:opacity-50"
                 :on-click #(rf/dispatch [:avaliar-locatario id @nota])}
        "Enviar"]])))

(defn- botao-pequeno [variante texto evento]
  [:button {:type     "button"
            :class    (str "flex-1 rounded-lg px-3 py-2 text-sm font-semibold "
                           (case variante
                             :primario "bg-primaria text-primaria-contraste"
                             :perigo   "border border-perigo text-perigo"
                             "border border-borda"))
            :on-click #(rf/dispatch evento)}
   texto])

(defn- cartao-pedido [{:keys [id status pagamento titulo locatario_nome inicio fim total_centavos ja_avaliei] :as p}]
  (let [paga? (= "captured" pagamento)]
    [:li.rounded-2xl.border.border-borda.bg-superficie.p-3
     [:div.flex.gap-3
      [c/foto-bike p "h-16 w-20 shrink-0 rounded-xl"]
      [:div.min-w-0.flex-1
       [:div.flex.items-start.justify-between.gap-2
        [:p.truncate.font-semibold titulo]
        [c/badge-status p]]
       [:p.text-sm (str locatario_nome " · " (c/periodo-texto {:inicio inicio :fim fim}))]
       [:p.text-sm.font-medium (c/preco total_centavos)]]]
     (case status
       "pending"
       [:div.mt-3.flex.gap-2
        [botao-pequeno :secundario "Recusar" [:acao-pedido "reject" id "Pedido recusado"]]
        [botao-pequeno :primario "Confirmar" [:acao-pedido "confirm" id "Reserva confirmada!"]]]

       "confirmed"
       [:div.mt-3
        (if paga?
          [:div.flex.gap-2
           [:button {:type "button" :class "flex-1 rounded-lg border border-perigo px-3 py-2 text-sm font-semibold text-perigo"
                     :on-click #(when (js/confirm "Cancelar esta reserva? O locatário recebe o valor de volta.")
                                  (rf/dispatch [:acao-pedido "cancel" id "Reserva cancelada e valor estornado"]))}
            "Cancelar"]
           [botao-pequeno :primario "Bike devolvida" [:acao-pedido "complete" id "Reserva concluída"]]]
          [:p.text-sm.text-suave "Aguardando o pagamento do locatário."])]

       "completed"
       (when-not (some #{"renter"} ja_avaliei)
         ^{:key id} [avaliar-locatario id])

       nil)]))

(defn- grupo [titulo pedidos]
  (when (seq pedidos)
    [:section.mb-6
     [:h2.mb-2.text-sm.font-semibold.uppercase.tracking-wide.text-suave (str titulo " (" (count pedidos) ")")]
     [:ul.space-y-3
      (for [p pedidos] ^{:key (:id p)} [cartao-pedido p])]]))

(defn pedidos []
  [:div
   [abas :pedidos]
   [c/conteudo :pedidos
    (fn [lista]
      (if (empty? lista)
        [c/vazio "Nenhum pedido ainda. Quando alguém reservar sua bike, aparece aqui."]
        (let [por-status (group-by :status lista)]
          [:div
           [grupo "Aguardando você" (por-status "pending")]
           [grupo "Confirmadas" (por-status "confirmed")]
           [grupo "Histórico" (concat (por-status "completed") (por-status "rejected") (por-status "cancelled"))]])))]])

;; ---------------------------------------------------------------- ganhos

(defn- soma [reservas] (reduce + 0 (map :total_centavos reservas)))

(def ^:private nomes-meses
  ["jan" "fev" "mar" "abr" "mai" "jun" "jul" "ago" "set" "out" "nov" "dez"])

(defn- mes-texto [aaaa-mm]
  (let [[ano mes] (str/split aaaa-mm #"-")]
    (str (nomes-meses (dec (js/parseInt mes))) "/" ano)))

(defn- indicador [rotulo valor detalhe]
  [:div.rounded-2xl.border.border-borda.bg-superficie.p-4
   [:p.text-sm.text-suave rotulo]
   [:p.mt-1.text-2xl.font-bold valor]
   [:p.text-xs.text-suave detalhe]])

(defn ganhos []
  [:div
   [abas :ganhos]
   [c/conteudo :pedidos
    (fn [lista]
      (let [pagas     (filter #(= "captured" (:pagamento %)) lista)
            recebidas (filter #(= "completed" (:status %)) pagas)
            a-receber (filter #(= "confirmed" (:status %)) pagas)
            por-bike  (->> pagas
                           (group-by :titulo)
                           (map (fn [[titulo rs]] [titulo (count rs) (soma rs)]))
                           (sort-by last >))
            por-mes   (->> recebidas
                           (group-by #(subs (:inicio %) 0 7))
                           (sort-by key >))]
        [:div.space-y-6
         [:div.grid.grid-cols-2.gap-3
          [indicador "Recebido" (c/preco (soma recebidas)) (str (count recebidas) " reserva(s) concluída(s)")]
          [indicador "A receber" (c/preco (soma a-receber)) (str (count a-receber) " reserva(s) paga(s) em andamento")]]
         (if (empty? pagas)
           [c/vazio "Seus ganhos aparecem aqui depois do primeiro pagamento."]
           [:<>
            [:section
             [:h2.mb-2.font-semibold "Por bike"]
             [:ul.divide-y.divide-borda.rounded-2xl.border.border-borda.bg-superficie
              (for [[titulo n total] por-bike]
                ^{:key titulo}
                [:li.flex.items-center.justify-between.gap-2.px-4.py-3
                 [:span.min-w-0.truncate titulo]
                 [:span.shrink-0.text-sm [:span.text-suave (str n "× · ")] [:span.font-semibold (c/preco total)]]])]]
            (when (seq por-mes)
              [:section
               [:h2.mb-2.font-semibold "Recebido por mês"]
               [:ul.divide-y.divide-borda.rounded-2xl.border.border-borda.bg-superficie
                (for [[mes rs] por-mes]
                  ^{:key mes}
                  [:li.flex.justify-between.px-4.py-3
                   [:span (mes-texto mes)]
                   [:span.font-semibold (c/preco (soma rs))]])]])])
         [:p.text-xs.text-suave "Pagamentos simulados: nenhum valor é transferido de verdade."]]))]])
