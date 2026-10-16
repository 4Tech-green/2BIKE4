(ns bikerent.http.auth-test
  (:require [bikerent.http.auth :as auth]
            [clojure.test :refer [deftest is]]))

(defn- identidade-com [secret header]
  (let [wrap    (:wrap auth/identity-middleware)
        handler (wrap :identity secret)]
    (handler {:headers (if header {"authorization" header} {})})))

(deftest token-valido-vira-identidade
  (let [t (auth/token "s3gredo" {:id 7 :papel "user"})]
    (is (= {:id 7 :papel "user"} (identidade-com "s3gredo" (str "Bearer " t))))))

(deftest token-invalido-ou-ausente
  (let [t (auth/token "outro" {:id 7 :papel "user"})]
    (is (nil? (identidade-com "s3gredo" (str "Bearer " t))))
    (is (nil? (identidade-com "s3gredo" "Bearer lixo")))
    (is (nil? (identidade-com "s3gredo" nil)))))
