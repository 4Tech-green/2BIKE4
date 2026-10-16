(ns bikerent.users.ports)

(defprotocol UserRepository
  (create! [repo user] "Insere o usuário e devolve o registro criado.")
  (find-by-email [repo email] "Usuário com o hash da senha, ou nil.")
  (find-by-id [repo id] "Usuário sem o hash da senha, ou nil."))
