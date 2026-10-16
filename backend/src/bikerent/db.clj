(ns bikerent.db
  "Pool de conexões com o Postgres, migrações (Migratus) e helpers de consulta."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [honey.sql :as sql]
            [honey.sql.pg-ops]
            [integrant.core :as ig]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [next.jdbc.date-time :as date-time]
            [next.jdbc.result-set :as rs])
  (:import (com.zaxxer.hikari HikariDataSource)
           (java.net URI)))

;; timestamptz chega como java.time.Instant, que vira texto ISO no JSON.
(date-time/read-as-instant)

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(defn execute!
  "Executa uma consulta HoneySQL e devolve as linhas com chaves simples (:id, :titulo...)."
  [ds query]
  (jdbc/execute! ds (sql/format query) opts))

(defn execute-one! [ds query]
  (jdbc/execute-one! ds (sql/format query) opts))

(defn daterange
  "Expressão HoneySQL para um daterange com as duas datas inclusivas."
  [{:keys [inicio fim]}]
  [:cast (str "[" inicio "," fim "]") :daterange])

(defn periodo-cols
  "Colunas :inicio e :fim (inclusivas, texto AAAA-MM-DD) de uma coluna daterange."
  [col]
  [[[:cast [:lower col] :text] :inicio]
   [[:cast [:- [:upper col] [:inline 1]] :text] :fim]])

(defn jdbc-url
  "Converte a URL no formato postgresql://usuario:senha@host/banco?params
  (Neon, Render, Supabase) para o formato aceito pelo driver JDBC.
  Usuário e senha seguem codificados; o driver decodifica os parâmetros."
  [database-url]
  (if (str/starts-with? database-url "jdbc:")
    database-url
    (let [uri          (URI. database-url)
          [user pass]  (some-> (.getRawUserInfo uri) (str/split #":" 2))
          params       (cond-> []
                         (.getRawQuery uri) (conj (.getRawQuery uri))
                         user               (conj (str "user=" user))
                         pass               (conj (str "password=" pass)))]
      (str "jdbc:postgresql://" (.getHost uri)
           (when (pos? (.getPort uri)) (str ":" (.getPort uri)))
           (.getRawPath uri)
           (when (seq params) (str "?" (str/join "&" params)))))))

(defn datasource [url pool-size]
  (when (str/blank? url)
    (throw (ex-info "DATABASE_URL não definida" {})))
  (connection/->pool HikariDataSource {:jdbcUrl         (jdbc-url url)
                                       :maximumPoolSize pool-size}))

(defn- migratus-config [ds]
  {:store         :database
   :migration-dir "migrations"
   :db            {:datasource ds}})

(defn migrate! [ds]
  (migratus/migrate (migratus-config ds)))

(defmethod ig/init-key ::datasource [_ {:keys [url pool-size migrate?]}]
  (let [ds (datasource url pool-size)]
    (when migrate?
      (log/info "Aplicando migrações pendentes")
      (migrate! ds))
    ds))

(defmethod ig/halt-key! ::datasource [_ ds]
  (.close ^HikariDataSource ds))

(defn -main
  "clojure -M:migrate [migrate|rollback|create <nome>]"
  [& [cmd nome]]
  (let [ds (datasource (System/getenv "DATABASE_URL") 1)]
    (try
      (case (or cmd "migrate")
        "migrate"  (migrate! ds)
        "rollback" (migratus/rollback (migratus-config ds))
        "create"   (migratus/create (assoc (migratus-config ds) :migration-dir "resources/migrations") nome))
      (finally
        (.close ^HikariDataSource ds)))))
