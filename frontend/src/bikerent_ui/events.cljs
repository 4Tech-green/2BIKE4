(ns bikerent-ui.events
  (:require [bikerent-ui.config :as config]
            [re-frame.core :as rf]
            [reitit.frontend.easy :as rfe]))

(def ^:private chave-sessao "bikerent/sessao")

;; ---------------------------------------------------------------- efeitos

(rf/reg-fx
 :navegar
 (fn [[nome params query]]
   (rfe/push-state nome params query)))

(rf/reg-fx
 :salvar-sessao
 (fn [sessao]
   (try
     (if sessao
       (.setItem js/localStorage chave-sessao (js/JSON.stringify (clj->js sessao)))
       (.removeItem js/localStorage chave-sessao))
     (catch :default _ nil))))

(rf/reg-fx
 :rolar-topo
 (fn [_] (js/window.scrollTo 0 0)))

(rf/reg-cofx
 :sessao-salva
 (fn [cofx]
   (assoc cofx :sessao-salva
          (try
            (some-> (.getItem js/localStorage chave-sessao) js/JSON.parse (js->clj :keywordize-keys true))
            (catch :default _ nil)))))

;; ---------------------------------------------------------------- início e rotas

(rf/reg-event-fx
 :iniciar
 [(rf/inject-cofx :sessao-salva)]
 (fn [{:keys [sessao-salva]} _]
   {:db (select-keys sessao-salva [:token :user])}))

(rf/reg-event-fx
 :rota-mudou
 (fn [{:keys [db]} [_ match]]
   (let [{:keys [name login? carregar]} (:data match)]
     (cond
       (nil? match)
       {:navegar [:inicio]}

       (and login? (not (:token db)))
       {:db      (assoc db :depois-do-login [name (:path-params match) (:query-params match)])
        :navegar [:entrar]}

       :else
       {:db         (assoc db :rota match)
        :rolar-topo nil
        :fx         (for [evento (when carregar
                                   (carregar {:path  (:path-params match)
                                              :query (:query-params match)}))]
                      [:dispatch evento])}))))

;; ---------------------------------------------------------------- leitura da API

(rf/reg-event-fx
 :carregar
 (fn [{:keys [db]} [_ chave req]]
   {:db  (cond-> (-> db
                     (assoc-in [:carregando chave] true)
                     (update :erros dissoc chave)
                     (assoc-in [:origem chave] req))
           ;; outra bike, outra busca: não mostrar os dados antigos enquanto carrega
           (not= req (get-in db [:origem chave])) (update :dados dissoc chave))
    :api (assoc req
                :token (:token db)
                :on-ok [:carregado chave]
                :on-erro [:falhou chave])}))

(rf/reg-event-db
 :carregado
 (fn [db [_ chave dados]]
   (-> db
       (assoc-in [:dados chave] dados)
       (update :carregando dissoc chave))))

(defn- sessao-expirou [db]
  {:db             (dissoc db :token :user :dados)
   :salvar-sessao  nil
   :navegar        [:entrar]
   :dispatch       [:avisar :erro "Sua sessão expirou. Entre de novo."]})

(rf/reg-event-fx
 :falhou
 (fn [{:keys [db]} [_ chave erro]]
   (if (and (= 401 (:status erro)) (:token db))
     (sessao-expirou db)
     {:db (-> db
              (assoc-in [:erros chave] erro)
              (update :carregando dissoc chave))})))

(rf/reg-event-fx
 :buscar
 ;; ver=mapa|lista só muda a exibição
 (fn [_ [_ {:keys [q] :as query}]]
   {:dispatch (if (seq q)
                [:carregar :resultados {:path "/search" :query {:q q}}]
                [:carregar :resultados {:path "/listings" :query (dissoc query :ver)}])}))

;; ---------------------------------------------------------------- escrita na API

(rf/reg-event-fx
 :enviar
 (fn [{:keys [db]} [_ req depois]]
   {:db  (assoc db :enviando? true)
    :api (assoc req
                :token (:token db)
                :on-ok [:enviado depois]
                :on-erro [:envio-falhou])}))

(rf/reg-event-fx
 :enviado
 (fn [{:keys [db]} [_ depois resposta]]
   {:db       (dissoc db :enviando?)
    :dispatch (conj depois resposta)}))

(rf/reg-event-fx
 :envio-falhou
 (fn [{:keys [db]} [_ erro]]
   (if (and (= 401 (:status erro)) (:token db))
     (sessao-expirou (dissoc db :enviando?))
     {:db       (dissoc db :enviando?)
      :dispatch [:avisar :erro (:mensagem erro)]})))

;; ---------------------------------------------------------------- avisos

(rf/reg-event-fx
 :avisar
 (fn [{:keys [db]} [_ tipo texto]]
   (let [id (random-uuid)]
     {:db (assoc db :aviso {:id id :tipo tipo :texto texto})
      :fx [[:dispatch-later {:ms 4000 :dispatch [:fechar-aviso id]}]]})))

(rf/reg-event-db
 :fechar-aviso
 (fn [db [_ id]]
   (cond-> db
     (or (nil? id) (= id (get-in db [:aviso :id]))) (dissoc :aviso))))

;; ---------------------------------------------------------------- sessão

(rf/reg-event-fx
 :login
 (fn [_ [_ form]]
   {:dispatch [:enviar {:method :post :path "/auth/login" :body form} [:entrou]]}))

(rf/reg-event-fx
 :cadastrar
 (fn [_ [_ form]]
   {:dispatch [:enviar {:method :post :path "/auth/register" :body form} [:entrou]]}))

(rf/reg-event-fx
 :entrou
 (fn [{:keys [db]} [_ {:keys [token user]}]]
   {:db            (-> db (assoc :token token :user user) (dissoc :depois-do-login))
    :salvar-sessao {:token token :user user}
    :navegar       (or (:depois-do-login db) [:inicio])
    :dispatch      [:avisar :sucesso (str "Olá, " (:nome user) "!")]}))

(rf/reg-event-fx
 :sair
 (fn [{:keys [db]} _]
   {:db            (dissoc db :token :user :dados)
    :salvar-sessao nil
    :navegar       [:inicio]}))

;; ---------------------------------------------------------------- reservas

(rf/reg-event-fx
 :reservar
 (fn [{:keys [db]} [_ pedido]]
   (if (:token db)
     {:dispatch [:enviar {:method :post :path "/bookings" :body pedido} [:reserva-criada]]}
     {:db       (assoc db :depois-do-login [:bike {:id (str (:listing_id pedido))}])
      :navegar  [:entrar]
      :dispatch [:avisar :info "Entre na sua conta para reservar"]})))

(rf/reg-event-fx
 :reserva-criada
 (fn [_ [_ reserva]]
   {:navegar  [:reserva {:id (:id reserva)}]
    :dispatch [:avisar :sucesso "Pedido enviado! Agora é só aguardar o dono confirmar."]}))

(defn- acao-na-reserva [acao mensagem]
  (fn [_ [_ id]]
    {:dispatch [:enviar {:method :post :path (str "/bookings/" id "/" acao)}
                [:reserva-atualizada mensagem]]}))

(rf/reg-event-fx :pagar (acao-na-reserva "pay" "Pagamento aprovado!"))
(rf/reg-event-fx :cancelar (acao-na-reserva "cancel" "Reserva cancelada"))

(rf/reg-event-fx
 :reserva-atualizada
 (fn [_ [_ mensagem reserva]]
   {:navegar    [:reserva {:id (:id reserva)}]
    :fx         [[:dispatch [:carregar :minhas-reservas {:path "/bookings/mine"}]]
                 [:dispatch [:avisar :sucesso mensagem]]]}))

;; ---------------------------------------------------------------- proprietário

(defn- recarregar-anuncio [id]
  [:carregar :anuncio {:path (str "/listings/" id)}])

(def ^:private recarregar-pedidos
  [:carregar :pedidos {:path "/bookings/received"}])

(rf/reg-event-fx
 :salvar-anuncio
 (fn [_ [_ id dados]]
   {:dispatch [:enviar (if id
                         {:method :put :path (str "/listings/" id) :body dados}
                         {:method :post :path "/listings" :body dados})
               [:anuncio-salvo (if id "Alterações salvas" "Bike anunciada! Agora adicione fotos.")]]}))

(rf/reg-event-fx
 :anuncio-salvo
 (fn [{:keys [db]} [_ mensagem anuncio]]
   {:db       (assoc-in db [:dados :anuncio] anuncio)
    :navegar  [:editar-anuncio {:id (:id anuncio)}]
    :dispatch [:avisar :sucesso mensagem]}))

(rf/reg-event-fx
 :mudar-status-anuncio
 (fn [_ [_ id status]]
   {:dispatch [:enviar {:method :put :path (str "/listings/" id) :body {:status status}}
               [:anuncio-atualizado (if (= "pausado" status) "Anúncio pausado" "Anúncio publicado")]]}))

(rf/reg-event-fx
 :anuncio-atualizado
 (fn [{:keys [db]} [_ mensagem anuncio]]
   {:db       (assoc-in db [:dados :anuncio] anuncio)
    :dispatch [:avisar :sucesso mensagem]}))

(rf/reg-event-fx
 :adicionar-foto
 (fn [_ [_ id url]]
   {:dispatch [:enviar {:method :post :path (str "/listings/" id "/photos") :body {:url url}}
               [:recarregar-anuncio id "Foto adicionada"]]}))

(rf/reg-event-fx
 :remover-foto
 (fn [_ [_ id foto-id]]
   {:dispatch [:enviar {:method :delete :path (str "/listings/" id "/photos/" foto-id)}
               [:recarregar-anuncio id "Foto removida"]]}))

(rf/reg-event-fx
 :bloquear-datas
 (fn [_ [_ id periodo]]
   {:dispatch [:enviar {:method :post :path (str "/listings/" id "/availability-blocks") :body periodo}
               [:anuncio-atualizado "Datas bloqueadas"]]}))

(rf/reg-event-fx
 :desbloquear-datas
 (fn [_ [_ id bloqueio-id]]
   {:dispatch [:enviar {:method :delete :path (str "/listings/" id "/availability-blocks/" bloqueio-id)}
               [:recarregar-anuncio id "Datas liberadas"]]}))

(rf/reg-event-fx
 :recarregar-anuncio
 (fn [_ [_ id mensagem]]
   {:fx [[:dispatch (recarregar-anuncio id)]
         [:dispatch [:avisar :sucesso mensagem]]]}))

(rf/reg-fx
 :upload-cloudinary
 (fn [{:keys [arquivo on-ok on-erro]}]
   (let [form (js/FormData.)]
     (.append form "file" arquivo)
     (.append form "upload_preset" config/CLOUDINARY_PRESET)
     (-> (js/fetch (str "https://api.cloudinary.com/v1_1/" config/CLOUDINARY_CLOUD "/image/upload")
                   #js {:method "POST" :body form})
         (.then #(if (.-ok %) (.json %) (throw (js/Error. "upload"))))
         (.then (fn [^js resposta] (rf/dispatch (conj on-ok (.-secure_url resposta)))))
         (.catch #(rf/dispatch on-erro))))))

(rf/reg-event-fx
 :enviar-foto
 (fn [{:keys [db]} [_ id arquivo]]
   {:db                (assoc db :enviando? true)
    :upload-cloudinary {:arquivo arquivo
                        :on-ok   [:foto-enviada id]
                        :on-erro [:envio-falhou {:mensagem "Não foi possível enviar a foto"}]}}))

(rf/reg-event-fx
 :foto-enviada
 (fn [{:keys [db]} [_ id url]]
   {:db       (dissoc db :enviando?)
    :dispatch [:adicionar-foto id url]}))

(rf/reg-event-fx
 :acao-pedido
 (fn [_ [_ acao id mensagem]]
   {:dispatch [:enviar {:method :post :path (str "/bookings/" id "/" acao)}
               [:pedido-atualizado mensagem]]}))

(rf/reg-event-fx
 :pedido-atualizado
 (fn [_ [_ mensagem]]
   {:fx [[:dispatch recarregar-pedidos]
         [:dispatch [:avisar :sucesso mensagem]]]}))

(rf/reg-event-fx
 :avaliar-locatario
 (fn [_ [_ id nota]]
   {:dispatch [:enviar {:method :post :path (str "/bookings/" id "/reviews") :body {:alvo "renter" :nota nota}}
               [:pedido-atualizado "Avaliação enviada"]]}))

;; ---------------------------------------------------------------- avaliação (locatário)

(rf/reg-event-fx
 :avaliar
 (fn [_ [_ id avaliacoes]]
   ;; uma requisição por alvo, em sequência
   (if-let [[alvo dados] (first avaliacoes)]
     {:dispatch [:enviar {:method :post :path (str "/bookings/" id "/reviews") :body (assoc dados :alvo alvo)}
                 [:avaliar id (rest avaliacoes)]]}
     {:navegar  [:reserva {:id id}]
      :fx       [[:dispatch [:carregar :minhas-reservas {:path "/bookings/mine"}]]
                 [:dispatch [:avisar :sucesso "Obrigado pela avaliação!"]]]})))
