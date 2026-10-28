(ns bikerent.reviews.ports)

(defprotocol ReviewRepository
  (create! [repo review] "Insere e devolve a avaliação; nil se o autor já avaliou esse alvo na reserva.")
  (list-by-listing [repo listing-id] "Avaliações da bike, mais recentes primeiro, com o nome do autor."))
