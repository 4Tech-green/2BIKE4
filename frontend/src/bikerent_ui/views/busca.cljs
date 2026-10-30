(ns bikerent-ui.views.busca
  "Home e resultados (lista e mapa) com filtros."
  (:require [bikerent-ui.views.comum :as c]
            [bikerent-ui.views.mapa :as mapa]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

(defn- buscar-texto! [texto]
  (when-not (str/blank? texto)
    (rfe/push-state :busca nil {:q (str/trim texto)})))

(def ^:private raio-km
  "O que conta como \"perto\" na home."
  30)

(defn- cartao-mini
  "Card compacto do carrossel sobre o mapa; o escolhido no mapa fica em destaque."
  [{:keys [id titulo diaria_centavos distancia cidade] :as bike} ativa?]
  [:a {:href  (rfe/href :bike {:id id})
       :ref   (fn [el]
                (when (and el ativa?)
                  (.scrollIntoView el #js {:behavior "smooth" :block "nearest" :inline "center"})))
       :class (str "flex w-72 shrink-0 snap-center gap-3 rounded-2xl border-2 bg-superficie p-2 shadow-lg "
                   (if ativa? "border-destaque" "border-transparent"))}
   [c/foto-bike bike "h-20 w-20 shrink-0 rounded-xl"]
   [:div.min-w-0.flex-1.py-1
    [:p.truncate.font-semibold titulo]
    [:p.truncate.text-sm.text-suave (if distancia (str (c/distancia-texto distancia) " · " cidade) cidade)]
    [:div.mt-1.flex.items-center.justify-between.gap-2
     [:span [:span.font-bold.text-destaque (c/preco diaria_centavos)] [:span.text-xs.text-suave " /dia"]]
     [c/nota-resumo bike]]]])

(defn- carrossel [bikes selecionada]
  ;; arrastar o carrossel só move os cards, nunca a página
  [:div {:class "flex snap-x snap-mandatory gap-3 overflow-x-auto overscroll-x-contain px-3 pb-3"}
   (for [b bikes]
     ^{:key (:id b)} [cartao-mini b (= selecionada (:id b))])])

(defn- botao-flutuante [attrs & filhos]
  (into [:button (merge {:type  "button"
                         :class "rounded-full bg-superficie px-4 py-2 text-sm font-semibold text-primaria shadow-md hover:bg-primaria/5"}
                        attrs)]
        filhos))

(defn inicio
  "Mapa em tela cheia: onde estou, para onde vou e que bikes estão livres hoje por perto."
  []
  (let [lugar (r/atom "")]
    (fn []
      (let [local        @(rf/subscribe [:local])
            localizando? @(rf/subscribe [:localizando?])
            selecionada  @(rf/subscribe [:bike-selecionada])
            dados        @(rf/subscribe [:dados :disponiveis-hoje])
            bikes        (c/com-distancia (or dados []) local)
            perto        (if local (filter #(<= (:distancia %) raio-km) bikes) bikes)
            onde         (cond (nil? local) "" (:nome local) (str " perto de " (:nome local)) :else " perto de você")]
        [:div {:class "tela-mapa"}
         [mapa/mapa-explorar {:bikes         bikes
                              :local         local
                              :selecionada   selecionada
                              :ao-selecionar #(rf/dispatch [:selecionar-bike %])}]

         [:div {:class "pointer-events-none absolute inset-x-0 top-0 z-[500] p-3"}
          [:div {:class "pointer-events-auto mx-auto max-w-xl space-y-2"}
           [:form {:class     "flex gap-2 rounded-2xl bg-superficie p-2 shadow-lg"
                   :on-submit (fn [e]
                                (.preventDefault e)
                                (when-not (str/blank? @lugar)
                                  (rf/dispatch [:buscar-lugar (str/trim @lugar)])))}
            [:input {:type        "search"
                     :value       @lugar
                     :on-change   #(reset! lugar (.. % -target -value))
                     :placeholder "Onde você está ou para onde vai?"
                     :class       "min-w-0 flex-1 rounded-xl px-3 py-2 outline-none"}]
            [:button {:type "submit" :class "rounded-xl bg-primaria px-4 font-semibold text-primaria-contraste hover:bg-primaria-hover"}
             "Ir"]]
           [:div.flex.flex-wrap.items-center.gap-2
            [botao-flutuante {:on-click #(rf/dispatch [:localizar false]) :disabled localizando?}
             (if localizando? "Localizando..." "◎ Perto de mim")]
            [:a {:href  (rfe/href :busca)
                 :class "rounded-full bg-superficie px-4 py-2 text-sm font-medium shadow-md hover:bg-primaria/5"}
             "Descrever o que procuro"]]]]

         [:div {:class "absolute inset-x-0 bottom-0 z-[500]"}
          (when dados
            [:<>
             [:p {:class "mx-3 mb-2 inline-block rounded-full bg-superficie px-3 py-1 text-sm font-medium shadow-md"}
              (cond
                (empty? bikes) "Nenhuma bike disponível hoje"
                (empty? perto) (str "Nenhuma bike a até " raio-km " km; veja as mais próximas")
                :else          (str (count perto) (if (= 1 (count perto)) " bike disponível" " bikes disponíveis")
                                    " hoje" onde))]
             (when (seq bikes)
               [carrossel (take 20 (if (seq perto) perto bikes)) selecionada])])]]))))

(defn- filtros [query ao-fechar]
  (let [form (r/atom (-> (select-keys query [:cidade :tipo :inicio :fim])
                         (assoc :preco_max (some-> (:preco_max query) js/parseInt (/ 100) str))))]
    (fn [_ ao-fechar]
      (let [campo (fn [k] {:value (get @form k "") :on-change #(swap! form assoc k (.. % -target -value))})
            aplicar (fn [e]
                      (.preventDefault e)
                      (let [{:keys [preco_max inicio fim] :as f} @form]
                        (if (not= (str/blank? inicio) (str/blank? fim))
                          (rf/dispatch [:avisar :erro "Escolha a data de retirada e a de devolução"])
                          (do (rfe/push-state :busca nil
                                              (cond-> (dissoc f :preco_max)
                                                (not (str/blank? preco_max))
                                                (assoc :preco_max (* 100 (js/parseInt preco_max)))))
                              (ao-fechar)))))]
        [:form {:on-submit aplicar :class "mb-4 space-y-3 rounded-2xl border border-borda bg-superficie p-4"}
         [:div {:class "grid grid-cols-1 gap-3 sm:grid-cols-2"}
          [c/campo "Cidade" (merge {:placeholder "Ex.: Recife"} (campo :cidade))]
          [:label.block
           [:span.mb-1.block.text-sm.font-medium "Tipo"]
           [:select (merge {:class "w-full rounded-xl border border-borda bg-superficie px-3 py-3"} (campo :tipo))
            [:option {:value ""} "Todos"]
            (for [[v nome] c/tipos] ^{:key v} [:option {:value v} nome])]]
          [c/campo "Diária máxima (R$)" (merge {:type "number" :min 1 :inputmode "numeric"} (campo :preco_max))]
          [:div.grid.grid-cols-2.gap-2
           [c/campo "Retirada" (merge {:type "date" :min (c/hoje)} (campo :inicio))]
           [c/campo "Devolução" (merge {:type "date" :min (or (:inicio @form) (c/hoje))} (campo :fim))]]]
         [:div.flex.gap-2
          [c/botao {:type "button" :variante :secundario :on-click #(do (rfe/push-state :busca) (ao-fechar))} "Limpar"]
          [c/botao {:type "submit"} "Aplicar filtros"]]]))))

(defn- alternar [ver query]
  [:div {:class "flex rounded-xl border border-borda bg-superficie p-1 text-sm"}
   (for [[valor nome] [["mapa" "Mapa"] ["lista" "Lista"]]]
     ^{:key valor}
     [:a {:href  (rfe/href :busca nil (assoc query :ver valor))
          :class (str "rounded-lg px-3 py-1 " (if (= ver valor) "bg-primaria text-primaria-contraste" "text-suave"))}
      nome])])

(defn resultados [query]
  (let [texto           (r/atom (:q query ""))
        filtros-abertos (r/atom false)]
    (fn [query]
      (let [ver         (if (= "lista" (:ver query)) "lista" "mapa")
            com-filtro  (seq (dissoc query :q :ver))
            local       @(rf/subscribe [:local])
            selecionada @(rf/subscribe [:bike-selecionada])]
        [:div
         [c/campo-busca {:valor @texto :ao-mudar #(reset! texto %) :ao-buscar buscar-texto!}]
         [:div.my-4.flex.items-center.justify-between.gap-2
          [:button {:on-click #(swap! filtros-abertos not)
                    :class    (str "rounded-xl border px-3 py-2 text-sm font-medium "
                                   (if com-filtro "border-primaria" "border-borda bg-superficie"))}
           (if com-filtro "Filtros ativos" "Filtros")]
          [alternar ver query]]
         (when @filtros-abertos
           [filtros query #(reset! filtros-abertos false)])
         [c/conteudo :resultados
          (fn [resultado]
            ;; busca por texto mantém a ordem de relevância; o resto vai do mais perto ao mais longe
            (let [bikes (if (:q query)
                          (map #(cond-> % (and local (:lat %)) (assoc :distancia (c/distancia-km local %))) resultado)
                          (c/com-distancia resultado local))]
              [:div
               [:p.mb-3.text-sm.text-suave
                (cond
                  (:q query)  (str (count bikes) " resultado(s) para “" (:q query) "”")
                  :else       (str (count bikes) " bike(s) disponível(is)"))]
               (cond
                 (empty? bikes)
                 [c/vazio "Nenhuma bike encontrada. Tente outras palavras ou menos filtros."]

                 (= ver "mapa")
                 [:div
                  [:div {:class "h-[60vh] overflow-hidden rounded-2xl border border-borda"}
                   [mapa/mapa-explorar {:bikes         bikes
                                        :local         local
                                        :selecionada   selecionada
                                        :ao-selecionar #(rf/dispatch [:selecionar-bike %])}]]
                  [:div {:class "-mx-3 mt-3"}
                   [carrossel bikes selecionada]]]

                 :else
                 [c/grade-bikes bikes])]))]]))))
