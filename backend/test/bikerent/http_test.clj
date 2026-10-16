(ns bikerent.http-test
  (:require [bikerent.http :as http]
            [bikerent.shared.errors :as errors]
            [clojure.test :refer [deftest is]])
  (:import (java.sql SQLException)))

(defn- responder-com [f]
  ((http/wrap-errors (fn [_] (f))) {:request-method :get :uri "/x"}))

(deftest erro-de-dominio-vira-status-http
  (is (= {:status 409 :body {:error {:code "conflict" :message "Ocupada"}}}
         (responder-com #(errors/error! :conflict "Ocupada"))))
  (is (= 403 (:status (responder-com #(errors/error! :forbidden "Não é o dono"))))))

(deftest sobreposicao-no-banco-vira-409
  (is (= 409 (:status (responder-com #(throw (SQLException. "overlap" "23P01")))))))

(deftest erro-inesperado-vira-500
  (is (= {:status 500 :body {:error {:code "internal" :message "Erro interno"}}}
         (responder-com #(throw (RuntimeException. "boom"))))))

(deftest rota-inexistente-responde-404-json
  (let [resp ((http/handler {:datasource nil}) {:request-method :get :uri "/nada"})]
    (is (= 404 (:status resp)))
    (is (= "application/json; charset=utf-8" (get-in resp [:headers "Content-Type"])))))
