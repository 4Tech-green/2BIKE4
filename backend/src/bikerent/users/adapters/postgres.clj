(ns bikerent.users.adapters.postgres
  (:require [bikerent.db :as db]
            [bikerent.users.ports :as ports]
            [integrant.core :as ig]))

(def ^:private cols [:id :nome :email :papel :foto_url :criado_em])

(defrecord PostgresUserRepository [ds]
  ports/UserRepository
  (create! [_ user]
    (db/execute-one! ds {:insert-into :users :values [user] :returning cols}))
  (find-by-email [_ email]
    (db/execute-one! ds {:select (conj cols :senha_hash) :from :users :where [:= :email email]}))
  (find-by-id [_ id]
    (db/execute-one! ds {:select cols :from :users :where [:= :id id]}))
  (update! [_ id changes]
    (db/execute-one! ds {:update :users :set changes :where [:= :id id] :returning cols})))

(defmethod ig/init-key ::repository [_ {:keys [datasource]}]
  (->PostgresUserRepository datasource))
