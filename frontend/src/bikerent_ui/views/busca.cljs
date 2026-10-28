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

(defn inicio []
  (let [texto (r/atom "")]
    (fn []
      [:div
       [:section.pt-2.pb-6
        [:h1 {:class "text-3xl font-bold leading-tight md:text-4xl"} "Alugue a bike certa, perto de você"]
        [:p.mt-2.text-suave "Descreva o que você precisa e a gente encontra."]]
       [c/campo-busca {:valor @texto :ao-mudar #(reset! texto %) :ao-buscar buscar-texto! :sugestoes? true}]

       [:section.mt-8
        [:h2.mb-3.text-lg.font-semibold "Por tipo"]
        [:div {:class "-mx-4 flex gap-2 overflow-x-auto px-4 pb-1"}
         (for [[valor nome] c/tipos]
           ^{:key valor}
           [:a {:href  (rfe/href :busca nil {:tipo valor})
                :class "shrink-0 rounded-xl border border-borda bg-superficie px-4 py-2 text-sm font-medium hover:border-primaria"}
            nome])]]

       [:section.mt-8
        [:div.mb-3.flex.items-baseline.justify-between
         [:h2.text-lg.font-semibold "Bikes recentes"]
         [:a.text-sm.text-suave.underline {:href (rfe/href :busca)} "Ver todas"]]
        [c/conteudo :recentes
         (fn [bikes]
           (if (seq bikes)
             [c/grade-bikes (take 9 bikes)]
             [c/vazio "Nenhuma bike anunciada ainda."]))]]])))

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
   (for [[valor nome] [["lista" "Lista"] ["mapa" "Mapa"]]]
     ^{:key valor}
     [:a {:href  (rfe/href :busca nil (assoc query :ver valor))
          :class (str "rounded-lg px-3 py-1 " (if (= ver valor) "bg-primaria text-primaria-contraste" "text-suave"))}
      nome])])

(defn resultados [query]
  (let [texto           (r/atom (:q query ""))
        filtros-abertos (r/atom false)]
    (fn [query]
      (let [ver        (if (= "mapa" (:ver query)) "mapa" "lista")
            com-filtro (seq (dissoc query :q :ver))]
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
          (fn [bikes]
            [:div
             [:p.mb-3.text-sm.text-suave
              (cond
                (:q query)  (str (count bikes) " resultado(s) para “" (:q query) "”")
                :else       (str (count bikes) " bike(s) disponível(is)"))]
             (cond
               (empty? bikes)   [c/vazio "Nenhuma bike encontrada. Tente outras palavras ou menos filtros."]
               (= ver "mapa")   [mapa/mapa bikes]
               :else            [c/grade-bikes bikes])])]]))))
