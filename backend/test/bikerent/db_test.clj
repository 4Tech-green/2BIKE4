(ns bikerent.db-test
  (:require [bikerent.db :as db]
            [clojure.test :refer [deftest is]]))

(deftest jdbc-url-converte-formato-neon
  (is (= "jdbc:postgresql://ep-x.sa-east-1.aws.neon.tech/neondb?sslmode=require&user=app&password=s3nh%40"
         (db/jdbc-url "postgresql://app:s3nh%40@ep-x.sa-east-1.aws.neon.tech/neondb?sslmode=require"))))

(deftest jdbc-url-mantem-porta
  (is (= "jdbc:postgresql://localhost:5432/bikes?user=u&password=p"
         (db/jdbc-url "postgres://u:p@localhost:5432/bikes"))))

(deftest jdbc-url-ja-no-formato-jdbc
  (is (= "jdbc:postgresql://localhost/bikes"
         (db/jdbc-url "jdbc:postgresql://localhost/bikes"))))
