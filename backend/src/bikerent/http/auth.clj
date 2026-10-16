(ns bikerent.http.auth
  "JWT: emissão do token e middlewares de identidade."
  (:require [bikerent.shared.errors :as errors]
            [buddy.sign.jwt :as jwt])
  (:import (java.time Instant)))

(def ^:private validade-segundos (* 7 24 60 60))

(defn token [secret user]
  (jwt/sign {:sub   (:id user)
             :papel (:papel user)
             :exp   (+ (.getEpochSecond (Instant/now)) validade-segundos)}
            secret
            {:alg :hs256}))

(defn- identidade [secret req]
  (when-let [[_ t] (re-matches #"(?i)Bearer\s+(.+)" (get-in req [:headers "authorization"] ""))]
    (try
      (let [claims (jwt/unsign t secret {:alg :hs256})]
        {:id (:sub claims) :papel (:papel claims)})
      (catch Exception _ nil))))

(def identity-middleware
  "Coloca {:id :papel} em :identity quando há um token válido."
  {:name ::identity
   :wrap (fn [handler secret]
           (fn [req]
             (handler (assoc req :identity (identidade secret req)))))})

(def require-login-middleware
  "Exige token nas rotas marcadas com `:login? true`. Fica antes da coerção,
  para quem não está logado receber 401 e não erros de validação."
  {:name    ::require-login
   :compile (fn [{:keys [login?]} _]
              (when login?
                (fn [handler]
                  (fn [req]
                    (if (:identity req)
                      (handler req)
                      (errors/error! :unauthorized "Faça login para continuar"))))))})
