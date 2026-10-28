(ns bikerent-ui.core
  (:require [bikerent-ui.api]
            [bikerent-ui.events]
            [bikerent-ui.routes :as routes]
            [bikerent-ui.subs]
            [bikerent-ui.views.app :as app]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]))

(defonce ^:private raiz
  (delay (rdc/create-root (js/document.getElementById "app"))))

(defn ^:dev/after-load montar []
  (rf/clear-subscription-cache!)
  (rdc/render @raiz [app/app]))

(defn init []
  (rf/dispatch-sync [:iniciar])
  (routes/iniciar!)
  (montar))
