(ns bikerent-ui.views.comum
  "Componentes reutilizáveis (mesmos nomes do Figma) e formatação."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [reitit.frontend.easy :as rfe]))

;; ---------------------------------------------------------------- formatação

(def ^:private moeda (js/Intl.NumberFormat. "pt-BR" #js {:style "currency" :currency "BRL"}))

(defn preco [centavos]
  (.format moeda (/ centavos 100)))

(defn data-curta
  "2026-11-10 → 10/11"
  [iso]
  (let [[_ m d] (str/split (str iso) #"-")]
    (str d "/" m)))

(defn periodo-texto [{:keys [inicio fim]}]
  (if (= inicio fim)
    (data-curta inicio)
    (str (data-curta inicio) " a " (data-curta fim))))

(defn hoje
  "Data local no formato AAAA-MM-DD."
  []
  (let [d   (js/Date.)
        dois #(.padStart (str %) 2 "0")]
    (str (.getFullYear d) "-" (dois (inc (.getMonth d))) "-" (dois (.getDate d)))))

(defn data<=
  "Compara datas AAAA-MM-DD (a ordem do texto é a ordem das datas)."
  [& datas]
  (every? (fn [[a b]] (not (pos? (compare a b)))) (partition 2 1 datas)))

(defn diarias
  "Datas inclusivas, como na API: de 10 a 12 são 3 diárias."
  [inicio fim]
  (inc (js/Math.round (/ (- (js/Date.parse fim) (js/Date.parse inicio)) 86400000))))

(def tipos
  [["urbana" "Urbana"] ["mountain" "Mountain"] ["speed" "Speed"] ["hibrida" "Híbrida"]
   ["eletrica" "Elétrica"] ["dobravel" "Dobrável"] ["infantil" "Infantil"]])

(defn tipo-nome [tipo]
  (or (some (fn [[v nome]] (when (= v tipo) nome)) tipos) tipo))

;; ---------------------------------------------------------------- básicos

(defn botao
  "variante: :primario (padrão), :destaque, :secundario ou :perigo."
  [{:keys [variante class] :as attrs} & filhos]
  (into [:button
         (-> attrs
             (dissoc :variante)
             (assoc :class (str "w-full rounded-xl px-4 py-3 font-semibold transition disabled:opacity-50 "
                                (case variante
                                  :secundario "border border-borda bg-superficie text-texto hover:border-primaria"
                                  :perigo     "border border-perigo text-perigo hover:bg-perigo/10"
                                  ;; laranja: só o CTA principal da tela, em negrito (contraste)
                                  :destaque   "bg-destaque font-bold text-white hover:bg-destaque-hover"
                                  "bg-primaria text-primaria-contraste hover:bg-primaria-hover")
                                " " class)))]
        filhos))

(defn campo
  "Rótulo + input. `attrs` vai direto para o input."
  [rotulo attrs]
  [:label.block
   [:span.mb-1.block.text-sm.font-medium rotulo]
   [:input (merge {:class "w-full rounded-xl border border-borda bg-superficie px-3 py-3 outline-none focus:border-primaria"}
                  attrs)]])

(defn carregando []
  [:div.flex.justify-center.py-16
   [:div {:class "h-8 w-8 animate-spin rounded-full border-4 border-borda border-t-primaria"}]])

(defn erro [{:keys [mensagem]}]
  [:div {:class "rounded-xl border border-perigo/30 bg-perigo/5 p-4 text-perigo"}
   (or mensagem "Algo deu errado")])

(defn vazio [texto & [acao]]
  [:div.py-16.text-center.text-suave
   [:p texto]
   (when acao [:div.mt-4 acao])])

(defn conteudo
  "Mostra carregando/erro/dados da chave carregada com :carregar.
  `render` é chamada como função (não como componente): uma fn anônima nova a
  cada render faria o React remontar a tela e os campos perderem o foco."
  [chave render]
  (let [dados       @(rf/subscribe [:dados chave])
        carregando? @(rf/subscribe [:carregando? chave])
        falha       @(rf/subscribe [:erro chave])]
    (cond
      falha          [erro falha]
      (some? dados)  (render dados)
      carregando?    [carregando]
      :else          [carregando])))

(defn titulo-pagina [texto & [voltar]]
  [:div.mb-4.flex.items-center.gap-2
   (when voltar
     [:a {:href voltar :class "-ml-2 rounded-full p-2 text-xl hover:bg-borda/50" :aria-label "Voltar"} "←"])
   [:h1.text-2xl.font-bold texto]])

;; ---------------------------------------------------------------- componentes do Figma

(defn estrelas
  "Estrelas de avaliação. Com `:ao-escolher` vira um seletor."
  [{:keys [nota ao-escolher tamanho]}]
  [:div.flex {:class (if (= tamanho :grande) "gap-1 text-3xl" "text-sm")}
   (for [i (range 1 6)]
     ^{:key i}
     [(if ao-escolher :button :span)
      (cond-> {:class (if (<= i (js/Math.round (or nota 0))) "text-destaque" "text-borda")}
        ao-escolher (assoc :type "button" :on-click #(ao-escolher i) :aria-label (str i " estrelas")))
      "★"])])

(defn nota-resumo [{:keys [nota_media total_avaliacoes]}]
  (if (pos? total_avaliacoes)
    [:span.flex.items-center.gap-1.text-sm
     [:span.text-destaque "★"] [:span.font-medium (.toFixed nota_media 1)] [:span.text-suave (str "(" total_avaliacoes ")")]]
    [:span.text-sm.text-suave "Nova"]))

(defn foto-bike [{:keys [foto titulo]} classe]
  (if foto
    [:img {:src foto :alt titulo :class (str "object-cover " classe)}]
    [:div {:class (str "flex items-center justify-center bg-primaria/5 " classe)}
     [:img {:src "brand/icone-pin.svg" :alt "" :class "h-1/3 max-h-16 w-auto"}]]))

(defn card-bike [{:keys [id titulo cidade tipo diaria_centavos] :as bike}]
  [:a {:href  (rfe/href :bike {:id id})
       :class "block overflow-hidden rounded-2xl border border-borda bg-superficie transition hover:shadow-md"}
   [foto-bike bike "aspect-[4/3] w-full"]
   [:div.p-3
    [:div.flex.items-start.justify-between.gap-2
     [:h3.truncate.font-semibold titulo]
     [nota-resumo bike]]
    [:p.text-sm.text-suave (str cidade " · " (tipo-nome tipo))]
    [:p.mt-1 [:span.font-bold.text-destaque (preco diaria_centavos)] [:span.text-sm.text-suave " / dia"]]]])

(defn grade-bikes [bikes]
  [:div {:class "grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"}
   (for [b bikes] ^{:key (:id b)} [card-bike b])])

(def ^:private status-info
  {"pending"   ["Aguardando o dono" "bg-alerta/10 text-alerta"]
   "confirmed" ["Confirmada" "bg-info/10 text-info"]
   "paga"      ["Paga" "bg-sucesso/10 text-sucesso"]
   "completed" ["Concluída" "bg-borda text-texto"]
   "rejected"  ["Recusada" "bg-perigo/10 text-perigo"]
   "cancelled" ["Cancelada" "bg-borda text-suave"]})

(defn badge-status [{:keys [status pagamento]}]
  (let [chave        (if (and (= "confirmed" status) (= "captured" pagamento)) "paga" status)
        [texto cor] (status-info chave [status "bg-borda"])]
    [:span {:class (str "inline-block rounded-full px-3 py-1 text-xs font-semibold " cor)} texto]))

(def sugestoes
  ["bike para trilha" "elétrica para ir ao trabalho" "dobrável para levar no metrô"
   "speed para pedalar na estrada" "bike infantil"])

(defn campo-busca
  "Campo de busca em linguagem natural, com sugestões opcionais."
  [{:keys [valor ao-mudar ao-buscar sugestoes?]}]
  [:div
   [:form.flex.gap-2 {:on-submit (fn [e] (.preventDefault e) (ao-buscar valor))}
    [:input {:type        "search"
             :value       valor
             :on-change   #(ao-mudar (.. % -target -value))
             :placeholder "O que você procura? Ex.: bike para trilha"
             :class       "min-w-0 flex-1 rounded-xl border border-borda bg-superficie px-4 py-3 outline-none focus:border-primaria"}]
    [:button {:type "submit" :class "rounded-xl bg-primaria px-4 font-semibold text-primaria-contraste hover:bg-primaria-hover"} "Buscar"]]
   (when sugestoes?
     [:div.mt-3.flex.flex-wrap.gap-2
      (for [s sugestoes]
        ^{:key s}
        [:button {:type     "button"
                  :on-click #(ao-buscar s)
                  :class    "rounded-full border border-borda bg-superficie px-3 py-1 text-sm hover:border-primaria"}
         s])])])

(defn aviso []
  (when-let [{:keys [tipo texto]} @(rf/subscribe [:aviso])]
    [:div {:class "fixed inset-x-0 top-4 z-[2000] flex justify-center px-4"}
     [:button {:on-click #(rf/dispatch [:fechar-aviso nil])
               :class    (str "max-w-md rounded-xl px-4 py-3 text-left text-sm font-medium shadow-lg "
                              (case tipo
                                :erro    "bg-perigo text-white"
                                :sucesso "bg-sucesso text-white"
                                "bg-primaria text-primaria-contraste"))}
      texto]]))

(defn- icone
  "Ícones de traço (24×24) que herdam a cor do texto."
  [& formas]
  (into [:svg {:viewBox "0 0 24 24" :fill "none" :stroke "currentColor" :stroke-width 2
               :stroke-linecap "round" :stroke-linejoin "round" :class "h-6 w-6" :aria-hidden true}]
        formas))

(def ^:private itens-menu
  ;; [rota texto ícone rotas-que-acendem-o-item]
  [[:inicio "Início" (icone [:path {:d "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"}])
    #{:inicio}]
   [:busca "Buscar" (icone [:circle {:cx 11 :cy 11 :r 7}] [:path {:d "m20 20-4-4"}])
    #{:busca :bike}]
   [:reservas "Reservas" (icone [:rect {:x 3 :y 4 :width 18 :height 17 :rx 2}] [:path {:d "M16 2v4M8 2v4M3 10h18"}])
    #{:reservas :reserva :pagar :avaliar}]
   [:meus-anuncios "Minhas bikes" (icone [:circle {:cx 5.5 :cy 17 :r 3.5}] [:circle {:cx 18.5 :cy 17 :r 3.5}]
                                         [:path {:d "M5.5 17 9 9h6l3.5 8M9 9 7.5 5.5H6M12 17l3-8"}])
    #{:meus-anuncios :novo-anuncio :editar-anuncio :pedidos :ganhos}]
   [:perfil "Perfil" (icone [:circle {:cx 12 :cy 8 :r 4}] [:path {:d "M4 21a8 8 0 0 1 16 0"}])
    #{:perfil}]])

(defn barra-navegacao
  "Barra de navegação inferior (celular)."
  [atual]
  [:nav {:class "fixed inset-x-0 bottom-0 z-[1000] border-t border-borda bg-superficie md:hidden"}
   [:ul {:class "mx-auto flex max-w-screen-sm justify-around pb-safe"}
    (for [[nome texto icone ativo] itens-menu]
      ^{:key nome}
      [:li
       [:a {:href  (rfe/href nome)
            :class (str "flex flex-col items-center gap-1 px-2 py-2 text-xs "
                        (if (ativo atual) "font-semibold text-primaria" "text-suave"))}
        icone texto]])]])

(defn cabecalho
  "Topo com a marca; no desktop também leva o menu."
  [atual]
  [:header {:class "sticky top-0 z-[1000] border-b border-borda bg-superficie/90 backdrop-blur"}
   [:div {:class "mx-auto flex max-w-5xl items-center justify-between px-4 py-2"}
    [:a {:href (rfe/href :inicio) :aria-label "2bike4, início"}
     [:img {:src "brand/logo-sem-slogan.svg" :alt "2bike4" :class "h-10 w-auto md:h-12"}]]
    [:nav {:class "hidden gap-6 md:flex"}
     (for [[nome texto _ ativo] itens-menu]
       ^{:key nome}
       [:a {:href  (rfe/href nome)
            :class (if (ativo atual) "font-semibold text-primaria" "text-suave hover:text-primaria")}
        texto])]]])
