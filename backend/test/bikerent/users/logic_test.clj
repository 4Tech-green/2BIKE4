(ns bikerent.users.logic-test
  (:require [bikerent.shared.errors :as errors]
            [bikerent.users.logic :as logic]
            [bikerent.users.ports :as ports]
            [clojure.test :refer [deftest is testing]]))

(defn- repo-em-memoria []
  (let [db (atom {})]
    (reify ports/UserRepository
      (create! [_ user]
        (let [u (assoc user :id (inc (count @db)) :papel "user")]
          (swap! db assoc (:id u) u)
          u))
      (find-by-email [_ email]
        (first (filter #(= email (:email %)) (vals @db))))
      (find-by-id [_ id]
        (dissoc (get @db id) :senha_hash)))))

(defn- tipo-do-erro [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e (errors/error-type e))))

(deftest cadastro-e-login
  (let [deps {:users (repo-em-memoria)}
        user (logic/register! deps {:nome " Ana " :email " Ana@X.com " :senha "segredo1"})]
    (testing "normaliza nome e e-mail e não expõe o hash"
      (is (= {:id 1 :nome "Ana" :email "ana@x.com" :papel "user"} user)))
    (testing "e-mail repetido é conflito, mesmo com outra caixa"
      (is (= :conflict (tipo-do-erro #(logic/register! deps {:nome "Outra" :email "ANA@x.com" :senha "segredo2"})))))
    (testing "login com a senha certa"
      (is (= 1 (:id (logic/login deps {:email "ana@x.com" :senha "segredo1"})))))
    (testing "senha errada ou e-mail desconhecido"
      (is (= :unauthorized (tipo-do-erro #(logic/login deps {:email "ana@x.com" :senha "errada"}))))
      (is (= :unauthorized (tipo-do-erro #(logic/login deps {:email "nao@x.com" :senha "segredo1"})))))))
