(ns bikerent-ui.config)

;; Definidos no build por variáveis de ambiente (ver shadow-cljs.edn).
(goog-define API_URL "http://localhost:3000/api/v1")

;; Upload de fotos direto para o Cloudinary (preset "unsigned"). Sem os dois,
;; o dono informa a URL de uma imagem já publicada.
(goog-define CLOUDINARY_CLOUD "")
(goog-define CLOUDINARY_PRESET "")

(defn upload-de-fotos? []
  (and (seq CLOUDINARY_CLOUD) (seq CLOUDINARY_PRESET)))
