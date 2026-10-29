(ns bikerent.http
  "Rotas Reitit, middlewares e servidor Jetty."
  (:require [bikerent.http.auth :as auth]
            [bikerent.http.bookings :as bookings]
            [bikerent.http.listings :as listings]
            [bikerent.http.search :as search]
            [bikerent.http.users :as users]
            [bikerent.shared.errors :as errors]
            [clojure.tools.logging :as log]
            [integrant.core :as ig]
            [malli.error :as me]
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

(defn- error-response
  ([status code message] {:status status :body (errors/body code message)})
  ([status code message details] {:status status :body (errors/body code message details)}))

(defn- detalhes-validacao [data]
  (try (me/humanize data) (catch Exception _ nil)))

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
            (error-response 400 :bad-request "Dados inválidos" (detalhes-validacao (ex-data e)))

            (= ex-type :muuntaja/decode)
            (error-response 400 :bad-request "JSON inválido")

            ;; restrição EXCLUDE ou triggers de sobreposição de datas
            (= sqlstate "23P01")
            (error-response 409 :conflict "As datas escolhidas não estão disponíveis")

            (= sqlstate "23505")
            (error-response 409 :conflict "Registro duplicado")

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

(defn routes [{:keys [datasource] :as deps}]
  (into ["/api/v1"
         ["/health" {:get {:handler (health datasource)}}]]
        (concat (users/routes deps)
                (listings/routes deps)
                (bookings/routes deps)
                (search/routes deps))))

(defn handler [{:keys [jwt-secret] :as deps}]
  (-> (ring/ring-handler
       (ring/router
        (routes deps)
        {:data {:coercion   malli-coercion/coercion
                :muuntaja   m/instance
                :middleware [parameters/parameters-middleware
                             muuntaja/format-negotiate-middleware
                             muuntaja/format-response-middleware
                             wrap-errors
                             [auth/identity-middleware jwt-secret]
                             auth/require-login-middleware
                             muuntaja/format-request-middleware
                             coercion/coerce-response-middleware
                             coercion/coerce-request-middleware]}})
       (ring/routes
        ;; no deploy o frontend compilado vem em resources/public (ver Dockerfile)
        (ring/create-resource-handler {:path "/"})
        (ring/create-default-handler
         {:not-found          (constantly (json-response 404 :not-found "Rota não encontrada"))
          :method-not-allowed (constantly (json-response 405 :method-not-allowed "Método não permitido"))})))
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
