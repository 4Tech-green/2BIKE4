(ns bikerent-ui.views.mapa
  "Mapas com Leaflet + OpenStreetMap. Os marcadores são HTML/círculos, não os
  ícones padrão do Leaflet, que dependem de imagens soltas fora do bundle."
  (:require ["leaflet" :as L]
            [reagent.core :as r]))

(def ^:private centro-brasil #js [-14.2 -51.9])

(defn- com-posicao [bikes]
  (filter #(and (:lat %) (:lng %)) bikes))

(defn- novo-mapa [no opcoes]
  (let [m (L/map no opcoes)]
    (-> (L/tileLayer "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                     #js {:maxZoom 19 :attribution "© OpenStreetMap"})
        (.addTo m))
    m))

(defn- etiqueta
  "Marcador em forma de etiqueta com o preço da diária (estilo em main.css)."
  [{:keys [diaria_centavos]} ativa?]
  (L/divIcon #js {:className ""
                  :iconSize  nil
                  :html      (str "<div class=\"pin-preco" (when ativa? " pin-preco-ativo") "\">R$ "
                                  (js/Math.round (/ diaria_centavos 100)) "</div>")}))

(defn- desenhar! [{:keys [camada]} {:keys [bikes local selecionada ao-selecionar]}]
  (.clearLayers camada)
  (when local
    (-> (L/circleMarker #js [(:lat local) (:lng local)]
                        #js {:radius 8 :weight 3 :color "#fff" :fillColor "#2563eb" :fillOpacity 1})
        (.addTo camada)))
  (doseq [b (com-posicao bikes)
          :let [ativa? (= selecionada (:id b))]]
    (-> (L/marker #js [(:lat b) (:lng b)]
                  #js {:icon (etiqueta b ativa?) :zIndexOffset (if ativa? 1000 0) :title (:titulo b)})
        (.on "click" #(ao-selecionar (:id b)))
        (.addTo camada))))

(defn- caber! [^js mapa pontos]
  (.fitBounds mapa (L/latLngBounds (clj->js (map (juxt :lat :lng) pontos)))
              ;; folga em cima para a busca e embaixo para o carrossel
              #js {:paddingTopLeft #js [40 140] :paddingBottomRight #js [40 150] :maxZoom 15}))

(defn- enquadrar!
  "Com local: ele e as 5 bikes mais próximas até 30 km (nenhuma tão perto: as 3
  mais próximas, onde estiverem). Sem local: todas as bikes."
  [^js mapa {:keys [bikes local]}]
  (let [pontos       (com-posicao bikes)
        por-distancia (sort-by :distancia (filter :distancia pontos))
        perto         (take 5 (take-while #(<= (:distancia %) 30) por-distancia))
        proximas      (if (seq perto) perto (take 3 por-distancia))]
    (cond
      (and local (seq proximas)) (caber! mapa (cons local proximas))
      local                      (.setView mapa #js [(:lat local) (:lng local)] 13)
      (seq pontos)               (caber! mapa pontos)
      :else                      (.setView mapa centro-brasil 4))))

(defn mapa-explorar
  "Mapa de bikes com etiquetas de preço e a posição de quem busca.
  props: {:bikes :local {:lat :lng} :selecionada id :ao-selecionar (fn [id])}."
  [_props]
  (let [no     (atom nil)
        estado (atom nil)]
    (r/create-class
     {:display-name "mapa-explorar"
      :component-did-mount
      (fn [this]
        (let [props (second (r/argv this))
              m     (novo-mapa @no #js {:zoomControl false})]
          (reset! estado {:mapa m :camada (.addTo (L/layerGroup) m)})
          (enquadrar! m props)
          (desenhar! @estado props)))
      :component-did-update
      (fn [this [_ antes]]
        (let [{:keys [mapa]} @estado
              props (second (r/argv this))
              sel   (some #(when (= (:selecionada props) (:id %)) %) (:bikes props))]
          (cond
            (not= (:local antes) (:local props))
            (enquadrar! mapa props)

            (and (nil? (:local props)) (not= (map :id (:bikes antes)) (map :id (:bikes props))))
            (enquadrar! mapa props)

            (and sel (:lat sel) (not= (:selecionada antes) (:selecionada props)))
            (.panTo ^js mapa #js [(:lat sel) (:lng sel)]))
          (desenhar! @estado props)))
      :component-will-unmount
      (fn [] (some-> @estado :mapa .remove))
      :reagent-render
      (fn [_]
        [:div {:ref #(reset! no %) :class "z-0 h-full w-full"}])})))

(defn seletor-local
  "Mapa para o dono marcar onde a bike fica. `ao-escolher` recebe [lat lng]."
  [_posicao _ao-escolher]
  (let [no     (atom nil)
        estado (atom nil)
        marcar! (fn [{:keys [mapa marcador] :as e} [lat lng] centralizar?]
                  (when lat
                    (if marcador
                      (.setLatLng marcador #js [lat lng])
                      (swap! estado assoc :marcador
                             (-> (L/circleMarker #js [lat lng]
                                                 #js {:radius 10 :weight 2 :color "#fff" :fillColor "#15803d" :fillOpacity 0.9})
                                 (.addTo mapa))))
                    (when centralizar? (.setView mapa #js [lat lng] (max 14 (.getZoom mapa)))))
                  e)]
    (r/create-class
     {:display-name "seletor-local"
      :component-did-mount
      (fn [this]
        (let [[_ [lat lng] ao-escolher] (r/argv this)
              m (novo-mapa @no #js {})]
          (if lat (.setView m #js [lat lng] 14) (.setView m centro-brasil 4))
          (reset! estado {:mapa m})
          (marcar! @estado [lat lng] false)
          (.on m "click" (fn [^js e]
                           (let [^js p (.-latlng e)]
                             ((:ao-escolher @estado ao-escolher) [(.-lat p) (.-lng p)]))))))
      :component-did-update
      (fn [this [_ antes]]
        (let [[_ posicao ao-escolher] (r/argv this)]
          (swap! estado assoc :ao-escolher ao-escolher)
          (when (not= antes posicao)
            (marcar! @estado posicao true))))
      :component-will-unmount
      (fn [] (some-> @estado :mapa .remove))
      :reagent-render
      (fn [_ _]
        [:div {:ref #(reset! no %) :class "z-0 h-64 w-full rounded-2xl border border-borda"}])})))
