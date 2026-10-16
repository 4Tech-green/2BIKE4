(ns bikerent.http
  "Rotas Reitit, middlewares e servidor Jetty."
  (:require [bikerent.shared.errors :as errors]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [muuntaja.core :as m]
            [next.jdbc :as jdbc]
            [reitit.coercion.malli :as malli-coercion]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.cors :refer [wrap-cors]])
  (:import (java.sql SQLException)
           (org.eclipse.jetty.server Server)))

(defn- error-response [status code message]
  {:status status :body (errors/body code message)})

(defn wrap-errors
  "Converte exceções no formato único de erro da API."
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (let [type     (errors/error-type e)
              ex-type  (:type (ex-data e))
              sqlstate (when (instance? SQLException e) (.getSQLState ^SQLException e))]
          (cond
            (errors/http-status type)
            (error-response (errors/http-status type) type (ex-message e))

            (= ex-type :reitit.coercion/request-coercion)
            (error-response 400 :bad-request "Dados inválidos")

            (= ex-type :muuntaja/decode)
            (error-response 400 :bad-request "JSON inválido")

            ;; restrição EXCLUDE ou triggers de sobreposição de datas
            (= sqlstate "23P01")
            (error-response 409 :conflict "As datas escolhidas não estão disponíveis")

            :else
            (do (log/error e "Erro não tratado em" (:request-method req) (:uri req))
                (error-response 500 :internal "Erro interno"))))))))

(defn- json-response
  "Resposta JSON para rotas fora do roteador (não passam pelo muuntaja)."
  [status code message]
  {:status  status
   :headers {"Content-Type" "application/json; charset=utf-8"}
   :body    (m/encode m/instance "application/json" (errors/body code message))})

(defn- health [datasource]
  (fn [_]
    (let [ok (:ok (jdbc/execute-one! datasource ["select 1 as ok"]))]
      {:status 200 :body {:status "ok" :db (= 1 ok)}})))

(defn routes [{:keys [datasource]}]
  ["/api/v1"
   ["/health" {:get {:handler (health datasource)}}]])

(defn handler [deps]
  (-> (ring/ring-handler
       (ring/router
        (routes deps)
        {:data {:coercion   malli-coercion/coercion
                :muuntaja   m/instance
                :middleware [parameters/parameters-middleware
                             muuntaja/format-negotiate-middleware
                             muuntaja/format-response-middleware
                             wrap-errors
                             muuntaja/format-request-middleware
                             coercion/coerce-response-middleware
                             coercion/coerce-request-middleware]}})
       (ring/create-default-handler
        {:not-found          (constantly (json-response 404 :not-found "Rota não encontrada"))
         :method-not-allowed (constantly (json-response 405 :method-not-allowed "Método não permitido"))}))
      ;; Web, Capacitor e Electron rodam em origens diferentes; o token vai no
      ;; cabeçalho Authorization (sem cookies), então liberar todas é seguro aqui.
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :post :put :patch :delete :options]
                 :access-control-allow-headers ["Authorization" "Content-Type"])))

(defmethod ig/init-key ::handler [_ deps]
  (handler deps))

(defmethod ig/init-key ::server [_ {:keys [handler port]}]
  (log/info "Servidor HTTP na porta" port)
  (jetty/run-jetty handler {:port port :join? false}))

(defmethod ig/halt-key! ::server [_ server]
  (.stop ^Server server))
