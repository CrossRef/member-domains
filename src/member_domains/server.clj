(ns member-domains.server
  (:require [member-domains.db :as db]
            [member-domains.etld :as etld])
  (:require [compojure.core :refer [context defroutes GET ANY POST]]
            [compojure.handler :as handler]
            [compojure.route :as route])
  (:require [ring.util.response :refer [redirect]])
  (:require [liberator.core :refer [defresource resource]]
            [liberator.representation :refer [ring-response]])
  (:require [selmer.parser :refer [render-file cache-off!]]
            [selmer.filters :refer [add-filter!]])
  (:require [clojure.data.json :as json])
  (:require [crossref.util.doi :as crdoi]
            [crossref.util.config :refer [config]])
  (:require [org.httpkit.server :refer [with-channel on-close on-receive send! run-server]])
  (:require [heartbeat.core :refer [def-service-check]]
            [heartbeat.ring :refer [wrap-heartbeat]]))

(def-service-check :mysql (fn [] (db/heartbeat)))

(selmer.parser/cache-off!)
   
; Just serve up a blank page with JavaScript to pick up from event-types-socket.
(defresource home
  []
  :available-media-types ["text/html"] 
  :handle-ok (fn [ctx]
               (let [info {:num-dois (db/num-resolved-dois)
                           :num-domains (db/num-domains)
                           :average-dois-per-member (db/average-dois-per-member)
                           :num-members (db/num-members)}]
                 (render-file "templates/home.html" info))))

(defresource data-full-domain-names
  []
  :available-media-types ["application/json"] 
  :handle-ok (fn [ctx]
               (let [domains (db/unique-member-domains)]
                 domains)))

(defresource data-domain-names
  []
  :available-media-types ["application/json"] 
  :handle-ok (fn [ctx]
               (let [full-domains (db/unique-member-domains)
                     domain-names (set (map #(->> % etld/get-main-domain (drop 1)) full-domains))]
                 (map #(clojure.string/join "." %) domain-names))))

(defresource member-prefixes
  []
  :available-media-types ["application/json"] 
  :handle-ok (fn [ctx]
               (let [prefixes (db/all-prefixes)]
                 prefixes)))

(defroutes app-routes
  (GET "/" [] (home))
  (GET "/data/full-domain-names.json" [] (data-full-domain-names))
  (GET "/data/domain-names.json" [] (data-domain-names))
  (GET "/data/member-prefixes.json" [] (member-prefixes))
  (route/resources "/"))

(defonce server (atom nil))

(def app
  (-> app-routes
      handler/site
      (wrap-heartbeat)))

(defn start []
  (reset! server (run-server #'app {:port (:port config)})))