#!/usr/bin/env boot
#tailrecursion.boot.core/version "2.5.1"

(set-env!
  :project      'photo-collage
  :version      "0.1.0-SNAPSHOT"
  :dependencies '[[tailrecursion/boot.task   "2.2.4"]
                  [tailrecursion/hoplon      "5.10.24"]
                  [tailrecursion/javelin     "3.7.2"]
                  [io.hoplon.vendor/jquery   "2.1.1-0"]
                  [com.cemerick/clojurescript.test "0.3.3"]
                  [io.hoplon/twitter.bootstrap "0.2.0"]]
  :out-path      "resources/public"
  :cljs-out-path "tests"
  :cljs-runner   "\\test-runner\\runner.js"
  :src-paths    #{"src"})


;; Static resources (css, images, etc.):
(add-sync! (get-env :out-path) #{"assets"})

(require
  '[tailrecursion.hoplon.boot    :refer :all]
  '[tailrecursion.boot.task.ring :refer [dev-server]]
  '[tailrecursion.boot.task :refer :all]
  '[clojure.java.shell :refer [sh]]
  )

(deftask development
  "Build photo-collage for development."
  []
  (comp (watch) (hoplon {:pretty-print true :prerender false}) (dev-server :port 3333)
        ))

(deftask dev-debug
  "Build photo-collage for development with source maps."
  []
  (comp (watch) (hoplon {:pretty-print true
                         :prerender false
                         :source-map true}) (dev-server :port 3333)
        ))

(deftask production
  "Build photo-collage for production."
  []
  (hoplon {:optimizations :advanced}))

(deftask cljs-compile
  "Simple clojurescript compiler task.. "
  []
  (cljs :output-path  "/tests/main.js" ))

(deftask cljs-test
  "Run clojurescript.test tests"
  []
   (let [current-dir (System/getProperty "user.dir"),
         slimer-home (System/getenv "SLIMER_HOME")]
     (let [result (sh (str slimer-home "\\slimerjs.bat")
                      (str current-dir (get-env :cljs-runner))
                      (str current-dir "\\" (get-env :out-path)
                                       "\\" (get-env :cljs-out-path)
                                       "\\main.js"))]
       (println (:out result))
))
   identity
)
