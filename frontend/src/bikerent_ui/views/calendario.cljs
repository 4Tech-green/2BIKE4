(ns bikerent-ui.views.calendario
  "Calendário de disponibilidade: um mês por vez, com dias reservados e bloqueados."
  (:require [bikerent-ui.views.comum :as c]
            [reagent.core :as r]))

(def ^:private meses
  ["Janeiro" "Fevereiro" "Março" "Abril" "Maio" "Junho" "Julho" "Agosto"
   "Setembro" "Outubro" "Novembro" "Dezembro"])

(defn- dois [n] (.padStart (str n) 2 "0"))

(defn- iso [ano mes dia]
  (str ano "-" (dois (inc mes)) "-" (dois dia)))

(defn- dentro? [data periodos]
  (some #(c/data<= (:inicio %) data (:fim %)) periodos))

(defn- legenda [classe texto]
  [:span.flex.items-center.gap-1
   [:span {:class (str "inline-block h-3 w-3 rounded " classe)}] texto])

(defn calendario
  "`reservados` e `bloqueios`: [{:inicio \"AAAA-MM-DD\" :fim ...}]."
  [_]
  (let [deslocamento (r/atom 0)]
    (fn [{:keys [reservados bloqueios]}]
      (let [hoje   (js/Date.)
            base   (js/Date. (.getFullYear hoje) (+ (.getMonth hoje) @deslocamento) 1)
            ano    (.getFullYear base)
            mes    (.getMonth base)
            vazios (.getDay base)
            dias   (.getDate (js/Date. ano (inc mes) 0))
            hoje-iso (iso (.getFullYear hoje) (.getMonth hoje) (.getDate hoje))]
        [:div.rounded-2xl.border.border-borda.bg-superficie.p-4
         [:div.mb-3.flex.items-center.justify-between
          [:button {:type "button" :class "rounded-lg px-3 py-1 hover:bg-fundo disabled:opacity-30"
                    :disabled (zero? @deslocamento) :on-click #(swap! deslocamento dec) :aria-label "Mês anterior"} "‹"]
          [:span.font-semibold (str (meses mes) " " ano)]
          [:button {:type "button" :class "rounded-lg px-3 py-1 hover:bg-fundo"
                    :on-click #(swap! deslocamento inc) :aria-label "Próximo mês"} "›"]]
         [:div {:class "grid grid-cols-7 gap-1 text-center text-xs"}
          (for [[i d] (map-indexed vector ["D" "S" "T" "Q" "Q" "S" "S"])]
            ^{:key i} [:span.py-1.text-suave d])
          (for [i (range vazios)]
            ^{:key (str "v" i)} [:span])
          (for [dia (range 1 (inc dias))
                :let [data (iso ano mes dia)]]
            ^{:key data}
            [:span {:class (str "rounded-lg py-2 "
                                (cond
                                  (neg? (compare data hoje-iso)) "text-borda"
                                  (dentro? data reservados)  "bg-info text-white"
                                  (dentro? data bloqueios)   "bg-suave/40 line-through"
                                  :else                      "bg-fundo"))}
             dia])]
         [:div.mt-3.flex.gap-4.text-xs.text-suave
          [legenda "bg-info" "Reservado"]
          [legenda "bg-suave/40" "Bloqueado"]
          [legenda "bg-fundo border border-borda" "Livre"]]]))))
