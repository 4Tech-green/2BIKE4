(ns bikerent.db
  "Pool de conexões com o Postgres e migrações (Migratus)."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [migratus.core :as migratus]
            [next.jdbc.connection :as connection])
  (:import (com.zaxxer.hikari HikariDataSource)
           (java.net URI)))

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
