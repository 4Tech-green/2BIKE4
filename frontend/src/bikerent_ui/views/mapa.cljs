(ns bikerent-ui.views.mapa
  "Mapa com Leaflet + OpenStreetMap. Usa círculos em vez dos ícones padrão do
  Leaflet, que dependem de imagens soltas fora do bundle."
  (:require ["leaflet" :as L]
            [bikerent-ui.views.comum :as c]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

(def ^:private centro-brasil #js [-14.2 -51.9])

(defn- com-posicao [bikes]
  (filter #(and (:lat %) (:lng %)) bikes))

(defn- popup [{:keys [id titulo diaria_centavos]}]
  (str "<a href=\"" (rfe/href :bike {:id id}) "\" style=\"font-weight:600\">" titulo "</a><br>"
       (c/preco diaria_centavos) " / dia"))

(defn- desenhar! [{:keys [mapa camada]} bikes]
  (.clearLayers camada)
  (let [pontos (com-posicao bikes)]
    (doseq [b pontos]
      (-> (L/circleMarker #js [(:lat b) (:lng b)]
                          #js {:radius 10 :weight 2 :color "#fff" :fillColor "#15803d" :fillOpacity 0.9})
          (.bindPopup (popup b))
          (.addTo camada)))
    (if (seq pontos)
      (.fitBounds mapa (L/latLngBounds (clj->js (map (juxt :lat :lng) pontos))) #js {:padding #js [40 40] :maxZoom 15})
      (.setView mapa centro-brasil 4))))

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
              m (L/map @no)]
          (-> (L/tileLayer "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                           #js {:maxZoom 19 :attribution "© OpenStreetMap"})
              (.addTo m))
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

(defn mapa [_bikes]
  (let [no     (atom nil)
        estado (atom nil)]
    (r/create-class
     {:display-name "mapa"
      :component-did-mount
      (fn [this]
        (let [m (L/map @no)]
          (-> (L/tileLayer "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                           #js {:maxZoom 19 :attribution "© OpenStreetMap"})
              (.addTo m))
          (reset! estado {:mapa m :camada (.addTo (L/layerGroup) m)})
          (desenhar! @estado (second (r/argv this)))))
      :component-did-update
      (fn [this] (desenhar! @estado (second (r/argv this))))
      :component-will-unmount
      (fn [] (some-> @estado :mapa .remove))
      :reagent-render
      (fn [bikes]
        [:div
         [:div {:ref #(reset! no %) :class "z-0 h-[65vh] w-full rounded-2xl border border-borda"}]
         (let [sem (- (count bikes) (count (com-posicao bikes)))]
           (when (pos? sem)
             [:p.mt-2.text-sm.text-suave (str sem " bike(s) sem localização não aparecem no mapa.")]))])})))
