(ns bikerent.search.domain
  (:require [clojure.string :as str]))

(def ^:private sobre-o-tipo
  "Palavras que ajudam a busca a ligar o tipo ao que as pessoas escrevem."
  {"urbana"   "urbana para cidade, passeio e trabalho"
   "speed"    "speed de estrada, asfalto e velocidade"
   "mountain" "mountain bike para trilha, terra e montanha"
   "hibrida"  "híbrida para cidade, estrada de terra e passeio"
   "eletrica" "elétrica com motor e bateria, sem esforço nas subidas"
   "infantil" "infantil para criança"
   "dobravel" "dobrável e compacta, fácil de levar no metrô e no carro"})

(defn texto-do-anuncio
  "Texto que vira o vetor do anúncio."
  [{:keys [titulo descricao tipo aro tamanho cidade]}]
  (->> [titulo
        (sobre-o-tipo tipo)
        descricao
        (when aro (str "aro " aro))
        (when tamanho (str "tamanho " tamanho))
        (str "em " cidade)]
       (remove str/blank?)
       (str/join ". ")))

(defn normalizar
  "Divide pelo comprimento, para a distância de cosseno; o vetor nulo fica como está."
  [vetor]
  (let [norma (Math/sqrt (reduce + (map #(* % %) vetor)))]
    (if (zero? norma)
      (vec vetor)
      (mapv #(/ % norma) vetor))))
