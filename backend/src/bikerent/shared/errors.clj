(ns bikerent.shared.errors
  "Erros de domínio. Os casos de uso lançam com `error!`; a camada HTTP
  converte para o formato único {:error {:code :message}}.")

(def http-status
  {:bad-request  400
   :unauthorized 401
   :forbidden    403
   :not-found    404
   :conflict     409})

(defn error!
  "Lança um erro de domínio. `type` é uma das chaves de `http-status`."
  ([type message] (error! type message {}))
  ([type message data]
   (throw (ex-info message (assoc data ::type type)))))

(defn error-type [e]
  (::type (ex-data e)))

(defn body [code message]
  {:error {:code (name code) :message message}})
