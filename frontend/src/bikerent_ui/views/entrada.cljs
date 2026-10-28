(ns bikerent-ui.views.entrada
  "Login e cadastro."
  (:require [bikerent-ui.views.comum :as c]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

(defn- valor [form k]
  {:value     (get @form k "")
   :on-change #(swap! form assoc k (.. % -target -value))})

(defn- moldura [titulo subtitulo & filhos]
  [:div {:class "mx-auto max-w-sm pt-6"}
   [:h1.text-3xl.font-bold titulo]
   [:p.mt-1.mb-6.text-suave subtitulo]
   (into [:div] filhos)])

(defn login []
  (let [form (r/atom {})]
    (fn []
      (let [enviando? @(rf/subscribe [:enviando?])]
        [moldura "Entrar" "Que bom ter você de volta."
         [:form.space-y-4 {:on-submit (fn [e] (.preventDefault e) (rf/dispatch [:login @form]))}
          [c/campo "E-mail" (merge {:type "email" :required true :auto-complete "email"} (valor form :email))]
          [c/campo "Senha" (merge {:type "password" :required true :auto-complete "current-password"} (valor form :senha))]
          [c/botao {:type "submit" :disabled enviando?} (if enviando? "Entrando..." "Entrar")]]
         [:p.mt-6.text-center.text-sm.text-suave
          "Ainda não tem conta? "
          [:a.font-semibold.text-texto.underline {:href (rfe/href :cadastro)} "Cadastre-se"]]]))))

(defn cadastro []
  (let [form (r/atom {})]
    (fn []
      (let [enviando? @(rf/subscribe [:enviando?])]
        [moldura "Criar conta" "Com uma conta você aluga e também anuncia sua bike."
         [:form.space-y-4 {:on-submit (fn [e] (.preventDefault e) (rf/dispatch [:cadastrar @form]))}
          [c/campo "Nome" (merge {:required true :min-length 2 :max-length 100 :auto-complete "name"} (valor form :nome))]
          [c/campo "E-mail" (merge {:type "email" :required true :auto-complete "email"} (valor form :email))]
          [c/campo "Senha (mínimo 6 caracteres)" (merge {:type "password" :required true :min-length 6 :auto-complete "new-password"} (valor form :senha))]
          [c/botao {:type "submit" :disabled enviando?} (if enviando? "Criando..." "Criar conta")]]
         [:p.mt-6.text-center.text-sm.text-suave
          "Já tem conta? "
          [:a.font-semibold.text-texto.underline {:href (rfe/href :entrar)} "Entrar"]]]))))
