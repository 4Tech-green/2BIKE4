(ns bikerent.users.logic
  "Casos de uso de usuários. `deps` traz os adaptadores: {:users UserRepository}."
  (:require [bikerent.shared.errors :as errors]
            [bikerent.users.domain :as domain]
            [bikerent.users.ports :as ports]
            [buddy.hashers :as hashers]
            [clojure.string :as str]))

(defn register! [{:keys [users]} {:keys [nome email senha]}]
  (let [email (domain/normalize-email email)]
    (when (ports/find-by-email users email)
      (errors/error! :conflict "E-mail já cadastrado"))
    (domain/public
     (ports/create! users {:nome       (str/trim nome)
                           :email      email
                           :senha_hash (hashers/derive senha {:alg :bcrypt+sha512})}))))

(defn login [{:keys [users]} {:keys [email senha]}]
  (let [user (ports/find-by-email users (domain/normalize-email email))]
    (if (and user (:valid (hashers/verify senha (:senha_hash user))))
      (domain/public user)
      (errors/error! :unauthorized "E-mail ou senha incorretos"))))

(defn me [{:keys [users]} user-id]
  (or (some-> (ports/find-by-id users user-id) domain/public)
      (errors/error! :not-found "Usuário não encontrado")))

(defn update-me!
  "Muda o nome e/ou a foto (URL; nil remove a foto)."
  [{:keys [users] :as deps} user-id changes]
  (let [changes (cond-> (select-keys changes [:nome :foto_url])
                  (:nome changes) (update :nome str/trim))]
    (if (seq changes)
      (or (some-> (ports/update! users user-id changes) domain/public)
          (errors/error! :not-found "Usuário não encontrado"))
      (me deps user-id))))
