(ns bikerent.listings.logic-test
  (:require [bikerent.listings.logic :as logic]
            [bikerent.listings.ports :as ports]
            [bikerent.shared.errors :as errors]
            [bikerent.shared.events :as events]
            [clojure.test :refer [deftest is testing]]))

(defn- repo-em-memoria []
  (let [db (atom {})]
    (reify ports/ListingRepository
      (create! [_ listing]
        (let [id (inc (count @db))]
          (swap! db assoc id (assoc listing :id id))
          id))
      (update! [_ id changes] (swap! db update id merge changes))
      (find-by-id [_ id] (get @db id))
      (search [_ filtros] {:filtros filtros}))))

(defn- eventos []
  (let [publicados (atom [])]
    (reify events/EventPublisher
      (publish! [_ tipo payload] (swap! publicados conj [tipo payload])))))

(defn- tipo-do-erro [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo e (errors/error-type e))))

(def ^:private dono {:id 10 :papel "user"})
(def ^:private outro {:id 20 :papel "user"})
(def ^:private admin {:id 30 :papel "admin"})

(deftest so-o-dono-ou-admin-altera
  (let [deps    {:listings (repo-em-memoria) :events (eventos)}
        listing (logic/create! deps dono {:titulo "Caloi" :diaria_centavos 3000})]
    (is (= 10 (:owner_id listing)))
    (is (= :forbidden (tipo-do-erro #(logic/update! deps outro (:id listing) {:titulo "Minha"}))))
    (is (= "Do dono" (:titulo (logic/update! deps dono (:id listing) {:titulo "Do dono"}))))
    (is (= "Moderado" (:titulo (logic/update! deps admin (:id listing) {:titulo "Moderado"}))))
    (is (= :not-found (tipo-do-erro #(logic/update! deps dono 999 {:titulo "x"}))))))

(deftest busca-por-periodo
  (let [deps {:listings (repo-em-memoria) :events (eventos)}]
    (testing "datas viram um período validado"
      (is (= "2026-11-10" (str (get-in (logic/search deps {:inicio "2026-11-10" :fim "2026-11-12"})
                                       [:filtros :periodo :inicio])))))
    (testing "só uma das datas"
      (is (= :bad-request (tipo-do-erro #(logic/search deps {:inicio "2026-11-10"})))))
    (testing "fim antes do início"
      (is (= :bad-request (tipo-do-erro #(logic/search deps {:inicio "2026-11-10" :fim "2026-11-09"})))))))
