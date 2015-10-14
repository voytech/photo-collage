(set-env!
  :project      'photo-collage
  :version      "0.1.0-SNAPSHOT"
  :dependencies '[[boot/core                       "2.0.0-rc10"] ;;rc10
                  [org.clojure/clojure             "1.7.0"]
                  [org.clojure/clojurescript       "0.0-3308"] ;; Need to explicitly provide 0.0-3308 as latest - otherwise it will take older version.
                  [org.clojure/tools.nrepl         "0.2.10"]
                  [adzerk/bootlaces                "0.1.10" :scope "test"]
                  [adzerk/boot-cljs                "0.0-3308-0" ] ;was:0.0-2814-3;new 0.0-3308-0 - inc.js ext.js no longer supported. Use cljsjs instead.
                  [adzerk/boot-cljs-repl           "0.1.9" :scope "test"] ;current is 0.1.9
                  [adzerk/boot-reload              "0.3.1"]
                  [adzerk/boot-test                "1.0.4" :scope "test"]
                  [pandeiro/boot-http              "0.6.3-SNAPSHOT"]
                  [tailrecursion/hoplon            "6.0.0-SNAPSHOT"] ;was:6.0.0-SNAPSHOT ;new 6.0.0-alpha4  - alpha4 doesnt work.
                  [tailrecursion/boot-hoplon       "0.1.1"]; was "0.1.0-SNAPSHOT" new 0.1.1
                  [tailrecursion/javelin           "3.8.0"];old 3.7.2 new 3.8.0
                  [tailrecursion/cljson            "1.0.7"]
                  [tailrecursion/castra            "2.2.2"]
                  [alandipert/storage-atom         "1.2.4"]
                  [io.hoplon.vendor/jquery         "2.1.1-0"]
                  [com.cemerick/clojurescript.test "0.3.3"]
                  [com.cemerick/friend             "0.2.1"]
                  [compojure                       "1.4.0"]
                  [bultitude                       "0.2.7"]
                  [com.datomic/datomic-free        "0.9.5206" :exclusions [commons-codec]] ; this clashes with castra.
                  [clj-tagsoup                     "0.3.0"]
                  [com.cognitect/transit-clj       "0.8.281"]
                  [ring/ring                       "1.3.2"]
                  [ring/ring-core                  "1.3.2"]
                  [ring/ring-jetty-adapter         "1.3.2"]
                  [ring/ring-session-timeout       "0.1.0"]
                  [ring/ring-mock                  "0.3.0"]
                  [cljsjs/fabric                   "1.5.0-0"]
                  [cljsjs/jquery                   "2.1.4-0"]
                  [cljsjs/jquery-ui                "1.11.3-1"]
                  [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                  [acyclic/squiggly-clojure        "0.1.3-SNAPSHOT"]
                  [voytech/boot-clojurescript.test "0.1.0-SNAPSHOT"]]
  :out-path       "resources/public"
  :target-path    "resources/public"
  :source-paths   #{"src/cljs" "src/clj" "src/hoplon" "src/test/clj"}
  :test-paths     #{"src/test/cljs" }
  :resource-paths #{"assets"}
  )

(require '[adzerk.boot-test :refer :all])
;;Support for cider-nrepl.
(require 'boot.repl)
(swap! boot.repl/*default-dependencies*
       concat '[[cider/cider-nrepl "0.10.0-SNAPSHOT"]]) ;;was 0.9.1

(swap! boot.repl/*default-middleware*
       conj 'cider.nrepl/cider-middleware)
;;End of cider-nrepl support.
;;Add support for flycheck-clojure
(require 'squiggly-clojure.core)

(require
  '[tailrecursion.boot-hoplon :refer :all]
  '[boot.util :refer [sh]]
  '[cemerick.cljs.test :refer :all]
  '[adzerk.boot-cljs :refer :all]
  '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
  '[adzerk.boot-reload    :refer [reload]]
  '[pandeiro.boot-http :refer [serve]]
  '[boot.core          :as boot]
  '[boot-clojurescript.test.tasks :refer :all]
  '[tailrecursion.castra.handler :as h :refer [castra]]
  '[app :refer [start running]]
  )


(deftask start-server-task [d docroot       PATH str  "The directory to serve."
                            p port          PORT int  "The port to listen on. (Default: 8000)"
                            j join          JOIN bool "Wait for server."]
  (let [join? (or join false)
        port! (or port 8080)
        path  (or docroot "resources/public" )]
    (boot/with-pre-wrap fileset
      (when (not @running) (start port! path join?))
      fileset)))

(deftask continous
  []
  (comp (serve)
        (watch)
        (hoplon)
        (reload)
        (cljs-repl)
        (cljs :source-map true :optimizations :none)
        ))

(deftask dev
  []
  (comp (hoplon) (cljs)))

(deftask dev-server []
  (comp (watch)
        (hoplon)
        (reload)
        (cljs-repl)
        (cljs :source-map true :optimizations :none)
        (start-server-task :port 3000
                           :join false)))

;;This task should give also reload and cljs-repl

(deftask tests-cljs
  "Run clojurescript.test tests"
  []
  (comp (watch) (cljs-tests)))

(deftask tests-clj
  "Run clojure tests"
  []
  (comp (test :namespaces #{'core.db.entity-mappings-tests
                            'core.db.manager-tests
                            'impl.services.public.auth-tests})))

(deftask continous-tests-clj []
  (comp (watch)
        (test :namespaces #{;core.db.entity-mappings-tests
                            'impl.services.public.auth-tests
                            'impl.services.tenant.manage-test
                            })))

(task-options!
 serve {
        :dir "resources/public"
        })
