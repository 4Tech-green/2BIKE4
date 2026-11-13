(ns bikerent-ui.core
  (:require [bikerent-ui.api]
            [bikerent-ui.events]
            [bikerent-ui.routes :as routes]
            [bikerent-ui.subs]
            [bikerent-ui.views.app :as app]
            [re-frame.core :as rf]
            [reagent.dom :as rdom]))

;; render "clássico" (modo React 17) e não createRoot: com createRoot o Reagent
;; 1.2 renderiza fora de hora e o React volta o texto antigo dos campos a cada
;; tecla — no teclado do celular o que já foi digitado passava a se repetir.
(defn ^:dev/after-load montar []
  (rf/clear-subscription-cache!)
  (rdom/render [app/app] (js/document.getElementById "app")))

(defn init []
  (rf/dispatch-sync [:iniciar])
  (routes/iniciar!)
  (montar))
