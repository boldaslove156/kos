(ns kos.element
  (:require
   [goog.dom :as dom]
   [mount.core :refer-macros [defstate]]
   [rum.core :as rum :refer-macros [defc defcs]]
   [taoensso.timbre :as tmb :include-macros true]
   [taoensso.encore :refer-macros [have]]
   [kos.db :as db]
   [kos.event :as evt]))

(defn el-value
  [e]
  (.-value (.-target e)))

(defcs new-user-form < (rum/local {} ::state)
  [state db-db event-dispatcher]
  (let [state_ (::state state)]
    [:div
     [:form
      [:p
       [:label {:for "user-id"} "Id"]
       [:input#user-id
        {:value     (:my/id @state_ "")
         :type      "text"
         :on-change (fn [e]
                      (swap! state_ assoc :my/id (el-value e)))}]]
      [:p
       [:label {:for "user-name"} "Name"]
       [:input#user-name
        {:value     (:my/name @state_ "")
         :type      "text"
         :on-change (fn [e]
                      (swap! state_ assoc :my/name (el-value e)))}]]
      [:p
       [:label {:for "user-email"} "Email"]
       [:input#user-email
        {:value     (:my/email @state_ "")
         :type      "text"
         :on-change (fn [e]
                      (swap! state_ assoc :my/email (el-value e)))}]]
      [:p
       [:label {:for "user-password"} "Password"]
       [:input#user-password
        {:value     (:my/password @state_ "")
         :type      "text"
         :on-change (fn [e]
                      (swap! state_ assoc :my/password (el-value e)))}]]
      [:p
       [:button
        {:on-click (fn []
                     (let [new-user {:db.entity/id  (:my/id @state_)
                                     :user/name     (:my/name @state_)
                                     :user/email    (:my/email @state_)
                                     :user/password (:my/password @state_)}
                           event    {:event/id :db-conn/add-users
                                     :users    [new-user]}]
                       (evt/dispatch! event-dispatcher event)
                       (reset! state_ {})))}
        "Submit"]]]]))

(defcs login-form < (rum/local {} ::state)
  [state db-db event-dispatcher]
  (let [state_ (::state state)]
    [:div
     [:form
      [:p
       [:label {:for "login-email"} "Email"]
       [:input#login-email
        {:value     (:my/email @state_ "")
         :type      "text"
         :on-change (fn [e]
                      (let [value (el-value e)]
                        (swap! state_ assoc :my/email value)))}]]
      [:p
       [:label {:for "login-password"} "Password"]
       [:input#login-password
        {:value     (:my/password @state_ "")
         :type      "text"
         :on-change (fn [e]
                      (let [value (el-value e)]
                        (swap! state_ assoc :my/password value)))}]]
      [:p
       [:button
        {:on-click
         (fn []
           (let [credential {:user/email    (:my/email @state_)
                             :user/password (:my/password @state_)}
                 event      {:event/id   :http-requester/request-authentication
                             :credential credential}]
             (evt/dispatch! event-dispatcher event)
             (reset! state_ {})))}
        "Login"]]]]))

(defc display-users
  [db-db event-dispatcher]
  [:div
   (let [users (db/q '{:find  [[(pull ?eid pattern) ...]]
                       :in    [$ pattern]
                       :where [[?eid :user/name]]}
                     db-db
                     [:db.entity/id
                      :user/name
                      :user/email
                      :user/password])]
     (if (seq users)
       [:ul
        (mapv (fn [user]
                [:li
                 [:ul
                  [:li (:db.entity/id user)]
                  [:li (:user/name user)]
                  [:li (:user/email user)]
                  [:li (:user/password user)]]])
              users)]
       [:h1 "No User!"]))])

(defc root-element
  [db-db event-dispatcher]
  [:div
   [:h1 "Hello World!"]
   (new-user-form db-db event-dispatcher)
   (login-form db-db event-dispatcher)
   (display-users db-db event-dispatcher)])

(defn mount-element!
  [db-db event-dispatcher]
  (let [node     (dom/getRequiredElement "app")
        mount-fn (fn [db-db]
                   (tmb/info "Mounting Element...")
                   (let [el (root-element db-db event-dispatcher)]
                     (rum/mount el node)))
        stop-fn  (fn []
                   (tmb/info "Unmounting Element...")
                   (rum/unmount node))]
    (mount-fn db-db)
    {:node     node
     :mount-fn mount-fn
     :stop-fn  stop-fn}))

(defn unmount-element!
  [element]
  ((:stop-fn element)))

(defstate element
  :start (mount-element! (db/db @db/db-conn) @evt/event-dispatcher)
  :stop (unmount-element! @element))
