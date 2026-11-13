(ns bi
  "Exporta os dados de rendimento dos donos de bike em CSV para o Power BI
  (ver bi/README.md). Só lê o banco; sobrescreve os arquivos de bi/dados/.

  DATABASE_URL=... clojure -M:bi"
  (:require [bikerent.db :as db]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

(def ^:private pasta "../bi/dados")

;; Valores em centavos (inteiros): o Power BI em português lê "10.50" como 1050.
;; As medidas dividem por 100 (ver bi/README.md).
(def ^:private consultas
  {"reservas"
   "select b.id as reserva_id, b.listing_id as bike_id, l.owner_id as dono_id,
           b.renter_id as locatario_id,
           lower(b.periodo) as inicio, upper(b.periodo) - 1 as fim,
           upper(b.periodo) - lower(b.periodo) as diarias,
           b.status, coalesce(p.status, 'sem_pagamento') as pagamento,
           b.total_centavos as valor_centavos,
           case when p.status = 'captured' then b.total_centavos else 0 end as receita_centavos
      from bookings b
      join listings l on l.id = b.listing_id
      left join payments p on p.booking_id = b.id
     order by b.id"

   "bikes"
   "select l.id as bike_id, l.owner_id as dono_id, l.titulo, l.tipo, l.cidade,
           l.diaria_centavos, l.status
      from listings l
     order by l.id"

   "donos"
   "select u.id as dono_id, u.nome,
           count(l.id) as total_bikes
      from users u
      join listings l on l.owner_id = u.id
     group by u.id
     order by u.id"

   ;; notas inteiras de 1 a 5 (a média fica para o Power BI)
   "avaliacoes"
   "select r.id as avaliacao_id, r.booking_id as reserva_id, b.listing_id as bike_id,
           r.alvo, r.nota, r.criado_em::date as data
      from reviews r
      join bookings b on b.id = r.booking_id
     order by r.id"})

(defn- celula [v]
  (cond
    (nil? v)    ""
    (string? v) (str "\"" (str/replace v "\"" "\"\"") "\"")
    :else       (str v)))

(defn- gravar!
  "`linhas` vem como arrays: a primeira é o cabeçalho (mantém a ordem das colunas)."
  [nome [colunas & linhas]]
  (let [arquivo (io/file pasta (str nome ".csv"))]
    (io/make-parents arquivo)
    ;; BOM no início: Power BI e Excel reconhecem o UTF-8 (acentos)
    (with-open [w (io/writer arquivo :encoding "UTF-8")]
      (.write w (str (char 0xFEFF)))
      (.write w (str (str/join "," (map name colunas)) "\n"))
      (doseq [linha linhas]
        (.write w (str (str/join "," (map celula linha)) "\n"))))
    (println (format "%-9s %4d linhas → %s" nome (count linhas) (.getCanonicalPath arquivo)))))

(defn -main [& _]
  (let [ds (db/datasource (System/getenv "DATABASE_URL") 1)]
    (try
      (doseq [[nome sql] consultas]
        (gravar! nome (jdbc/execute! ds [sql] {:builder-fn rs/as-unqualified-arrays})))
      (finally
        (.close ds)))))
