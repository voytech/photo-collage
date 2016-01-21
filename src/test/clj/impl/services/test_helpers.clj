(ns impl.services.test-helpers
  (:require [tailrecursion.cljson  :as e :refer [cljson->clj clj->cljson]]
            [core.db.schemap :refer [persist-schema]]
            [impl.db.schema :refer [db-url]]
            [app :refer [app-handler]]
            [clojure.test :refer :all]
            [datomic.api :as d]
            [ring.mock.request :as mock]))

(def abspath "/home/voytech/programming/github/photo-collage/resources")

(defn drop-all []
  (doseq [dbname (d/get-database-names (str (db-url) "*"))]
    (d/delete-database (str (db-url) dbname))))

(defn reload-db [name url]
  (d/delete-database url)
  (let [f (future (Thread/sleep 70000)
                  (when-let [res (d/create-database url)]
                    (when-let [connection (d/connect url)]
                      (persist-schema name url))))]
    @f))

(defn mock-castra
  ([path q-func payload]
   (-> (mock/request :post path)
       (mock/header "X-Castra-Csrf" "true")
       (mock/header "X-Castra-Tunnel" "cljson")
       (mock/header "Accept" "application/json")
       (mock/body (clj->cljson (if (not (nil? payload)) [q-func payload] [q-func])))))
  ([path q-func] (mock-castra path q-func nil)))

(defn mock-login [path username password]
  (-> (mock/request :post path)
      (mock/header "Authentication" (clj->cljson [username password]))
      (mock/header "Accept" "application/json")))

(defn parse-resp [resp]
  {:body (-> resp
             :body
             cljson->clj)
   :status (:status resp)})

(defn response-session [response]
  (-> response
      :headers
      (get "Set-Cookie")
      (first)))

(defn with-session [request sessionid]
  (mock/header request "cookie" sessionid))

(defn auth-request [path username password]
  ((app-handler abspath) (mock-login path username password)))

(defn castra-request
  ([endpoint qualified-rpc payload sessionid]
   (let [mock-castra-def (mock-castra endpoint qualified-rpc payload)
         response ((app-handler abspath) (if (not (nil? sessionid))
                                           (with-session
                                             mock-castra-def
                                             sessionid)
                                           mock-castra-def))
         clj-resp (parse-resp response)]
     (is (= (:status clj-resp) 200))
     (println (str "Response: " clj-resp))
     response))
  ([endpoint qualified-rpc payload]
   (castra-request endpoint qualified-rpc payload nil)))
