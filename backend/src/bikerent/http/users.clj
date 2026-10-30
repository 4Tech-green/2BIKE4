(ns bikerent.http.users
  (:require [bikerent.http.auth :as auth]
            [bikerent.users.logic :as logic]))

(def ^:private Email [:re {:error/message "e-mail inválido"} #"^[^@\s]+@[^@\s]+\.[^@\s]+$"])

(defn- com-token [secret status user]
  {:status status :body {:token (auth/token secret user) :user user}})

(defn routes [{:keys [jwt-secret] :as deps}]
  [["/auth/register"
    {:post {:parameters {:body [:map
                                [:nome [:string {:min 2 :max 100}]]
                                [:email Email]
                                [:senha [:string {:min 6 :max 100}]]]}
            :handler    (fn [{{:keys [body]} :parameters}]
                          (com-token jwt-secret 201 (logic/register! deps body)))}}]

   ["/auth/login"
    {:post {:parameters {:body [:map [:email :string] [:senha :string]]}
            :handler    (fn [{{:keys [body]} :parameters}]
                          (com-token jwt-secret 200 (logic/login deps body)))}}]

   ["/me"
    {:login? true
     :get    {:handler (fn [{:keys [identity]}]
                         {:status 200 :body (logic/me deps (:id identity))})}
     :put    {:parameters {:body [:map
                                  [:nome {:optional true} [:string {:min 2 :max 100}]]
                                  [:foto_url {:optional true} [:maybe [:re #"^https://.+"]]]]}
              :handler    (fn [{:keys [identity] {:keys [body]} :parameters}]
                            {:status 200 :body (logic/update-me! deps (:id identity) body)})}}]])
