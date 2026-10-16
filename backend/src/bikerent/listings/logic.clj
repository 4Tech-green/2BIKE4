(ns bikerent.listings.logic
  "Casos de uso de anúncios. `deps` traz {:listings ListingRepository};
  `user` é a identidade do token: {:id :papel}."
  (:require [bikerent.listings.domain :as domain]
            [bikerent.listings.ports :as ports]
            [bikerent.shared.errors :as errors]
            [bikerent.shared.time :as time]))

(defn get-listing [{:keys [listings]} id]
  (or (ports/find-by-id listings id)
      (errors/error! :not-found "Anúncio não encontrado")))

(defn- get-editable [deps id user]
  (let [listing (get-listing deps id)]
    (when-not (domain/pode-editar? listing user)
      (errors/error! :forbidden "Só o dono pode alterar este anúncio"))
    listing))

(defn search [{:keys [listings]} {:keys [inicio fim] :as filtros}]
  (when (not= (some? inicio) (some? fim))
    (errors/error! :bad-request "Informe a data inicial e a final"))
  (ports/search listings (cond-> (dissoc filtros :inicio :fim)
                           inicio (assoc :periodo (time/periodo inicio fim)))))

(defn mine [{:keys [listings]} user]
  (ports/list-by-owner listings (:id user)))

(defn create! [{:keys [listings] :as deps} user input]
  (get-listing deps (ports/create! listings (assoc input :owner_id (:id user)))))

(defn update! [{:keys [listings] :as deps} user id changes]
  (get-editable deps id user)
  (ports/update! listings id changes)
  (get-listing deps id))

(defn add-photo! [{:keys [listings] :as deps} user id url]
  (get-editable deps id user)
  (ports/add-photo! listings id url))

(defn remove-photo! [{:keys [listings] :as deps} user id photo-id]
  (get-editable deps id user)
  (when-not (ports/remove-photo! listings id photo-id)
    (errors/error! :not-found "Foto não encontrada")))

(defn add-block! [{:keys [listings] :as deps} user id {:keys [inicio fim]}]
  (get-editable deps id user)
  (ports/add-block! listings id (time/periodo inicio fim))
  (get-listing deps id))

(defn remove-block! [{:keys [listings] :as deps} user id block-id]
  (get-editable deps id user)
  (when-not (ports/remove-block! listings id block-id)
    (errors/error! :not-found "Bloqueio não encontrado")))
