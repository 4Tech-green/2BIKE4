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
        (dissoc (get @db id) :senha_hash))
      (update! [_ id changes]
        (when (get @db id)
          (dissoc (get (swap! db update id merge changes) id) :senha_hash))))))

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

(deftest atualizar-perfil
  (let [deps {:users (repo-em-memoria)}
        {:keys [id]} (logic/register! deps {:nome "Ana" :email "ana@x.com" :senha "segredo1"})]
    (testing "foto e nome novos"
      (is (= {:nome "Ana Souza" :foto_url "https://fotos/ana.jpg"}
             (select-keys (logic/update-me! deps id {:nome " Ana Souza " :foto_url "https://fotos/ana.jpg"})
                          [:nome :foto_url]))))
    (testing "nil remove a foto; campos fora do perfil são ignorados"
      (let [user (logic/update-me! deps id {:foto_url nil :papel "admin"})]
        (is (nil? (:foto_url user)))
        (is (= "user" (:papel user)))))
    (testing "conta inexistente"
      (is (= :not-found (tipo-do-erro #(logic/update-me! deps 99 {:nome "X"})))))))
