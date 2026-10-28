(ns bikerent-ui.api
  "Efeito :api — chama a API com fetch e despacha `on-ok` com o corpo, ou
  `on-erro` com {:status :mensagem}."
  (:require [bikerent-ui.config :as config]
            [clojure.string :as str]
            [re-frame.core :as rf]))

(defn- query-string [params]
  (->> params
       (remove (fn [[_ v]] (or (nil? v) (= "" v))))
       (map (fn [[k v]] (str (js/encodeURIComponent (name k)) "=" (js/encodeURIComponent v))))
       (str/join "&")))

(defn- url [path query]
  (let [qs (query-string query)]
    (cond-> (str config/API_URL path)
      (seq qs) (str "?" qs))))

(defn- ler-resposta [resp]
  (if (= 204 (.-status resp))
    (js/Promise.resolve {:status 204})
    (-> (.json resp)
        (.then (fn [json] {:status (.-status resp)
                           :body   (js->clj json :keywordize-keys true)})))))

(rf/reg-fx
 :api
 (fn [{:keys [method path query body token on-ok on-erro]}]
   (let [headers (cond-> {"Accept" "application/json"}
                   body  (assoc "Content-Type" "application/json")
                   token (assoc "Authorization" (str "Bearer " token)))]
     (-> (js/fetch (url path query)
                   (clj->js (cond-> {:method  (str/upper-case (name (or method :get)))
                                     :headers headers}
                              body (assoc :body (js/JSON.stringify (clj->js body))))))
         (.then ler-resposta)
         (.then (fn [{:keys [status body]}]
                  (if (< status 300)
                    (rf/dispatch (conj on-ok body))
                    (rf/dispatch (conj on-erro {:status   status
                                                :mensagem (get-in body [:error :message] "Algo deu errado")})))))
         (.catch (fn [_]
                   (rf/dispatch (conj on-erro {:status 0 :mensagem "Sem conexão com o servidor"}))))))))
