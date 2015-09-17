(ns core.db.entities
  (:require [datomic.api :as d]
            [clojure.walk :refer [prewalk]]))

; Below mapping should be defined using concise macro defined api as follows:
; (defentity user
;   (from :username to :user/name     with {:required true, unique true})
;   (from :password to :user/password with {:required true})
;   (from :roles    to :user/roles    with {:required true})
;   (from :tenant   to :user/tenant   with {:lookup-ref #([:user/name %])})
;   (from :messages to :user/message  with {:relation {:type 'message}
;                                           }

; Above defentity is a macro and from is also macro which should be expanded.
; Entity will add datomic attribute :shared/type representing type of mapping, when pulling entities.

; NEW APPROACH (I THINK MUCH BETTER):
; (defentity user.info  ;this is a prefix for attribute to define entity
;   (property name :username   type :db.type/string  with {:required true, unique true})
;   (property name :roles      type :db.type/ref    cardinality :db.cardinality/many  mapping-opts {:required true})
;   (property name :tenant     type :db.type/ref    cardinality :db.cardinality/one   mapping-opts {:lookup-ref #([:user.info/name %])})
;   (property name :messages   type :db.type/ref    cardinality :db.cardinality/many  mapping-opts {:r:elated-type 'message}
; Approach above will allow to:
; 1. Perform mapping from service entities into database entities.
; 2. Perform mapping from database entity into service entities.
; 3. Create attribute schema from this entity schema.
; 4. Automatically convert specific properties into lookup refs to establish relation.
; 5. Persist nested component entities - automatically resolved from service entity

(declare map-entity
         var-by-symbol
         reverse-mapping?)

(def DEFAULT_PARTITION :db.part/user)

(def ENTITY_TYPE_ATTRIB
  {:db/id #db/id[:db.part/db]
   :db/ident :entity/type
   :db/valueType :db.type/ref
   :db/cardinality :db.cardinality/one
   :db/doc "Entity level shema attribute - name of entity"
   :db.install/_attribute :db.part/db})

(def ^:dynamic mapping-opts {})
(def ^:dynamic entities-frequencies {})
(def ^:dynamic schema [])

(defn make-temp-id []
  (let [partition (or (:db-partition mapping-opts)
                      DEFAULT_PARTITION)]
    (d/tempid partition)
    ))

(defn- connect []
  (d/connect (:db-url mapping-opts)))

(defn- initialize-database []
  (println "Initializing database...")
  (let [connection-string (:db-url mapping-opts)]
    (d/create-database connection-string)))

(defn mapping-into-ns [mapping-symbol]
  (symbol (str "core.db.entities/" (name mapping-symbol))))

(defn db-mapping-type [mapping-type]
  (keyword (name mapping-type)))

(defn- mapping-enum [entity-name]
  [{:db/id (d/tempid :db.part/user),
    :db/ident (db-mapping-type entity-name)}])

(defn var-by-symbol [symbol]
  (-> symbol resolve var-get))

(defn inject-def [ns var-name value]
  (intern ns var-name value))

(defn make-var [symbol val]
  (intern 'core.db.entities symbol val))

(defn del-var [symb]
  (ns-unmap 'core.db.entities symb))

(defn get-var [symb]
  (->> symb
       name
       (str "core.db.entities/")
       symbol
       resolve
       var-get))

(defn get-frequencies []
  (var-by-symbol 'core.db.entities/entities-frequencies))


(defn- create-db-property [property-def]
  (-> {:db/id (d/tempid :db.part/db)
       :db/ident (:to-property property-def)
       :db/valueType (:type property-def)
       :db.install/_attribute :db.part/db}
      (merge (if-let [uniq  (:unique property-def)] {:db/unique uniq} {}))
      (merge (if-let [cmpnt (:component? property-def)] {:db/isComponent cmpnt} {}))
      (merge (if-let [card  (:cardinality property-def)] {:db/cardinality card} {:db/cardinality :db.cardinality/one}))))

(defn- append-schema [next-db-property]
  (def schema (conj schema next-db-property)))

(defn persist-schema []
  (d/transact (connect) schema))

(defn- do-check [key val]
  (when-not (and (symbol? key)
                 (contains? #{'name 'type 'cardinality 'mapping-opts 'index 'unique 'component?} key))
    (throw (IllegalArgumentException. (str "Wrong description of rule (" first "," val ")")))))

(defn- decode-args [args]
  (let [partitioned (partition 2 args)]
    (->> (doall (mapv (fn [p] (do-check (first p) (last p))
                        {(keyword (first p)), (last p)}) partitioned))
         (apply merge))))

(defmacro property [& args]
  (let [decoded (decode-args args)
        prop-name (:name decoded)
        entity-name (name (get-var 'curr-entity-name))
        to-property (keyword (str entity-name "/" (name (or (:to-property decoded) prop-name))))
        property-def (assoc decoded :to-property to-property)]
    (if (not (get-var 'flag))
      (do (append-schema (create-db-property property-def))
          {(:name decoded)
           (dissoc (assoc decoded :to-property to-property) :name)})
      {to-property {:to-property prop-name}})))

(defn concat-into [& items]
  (into [] (apply concat items)))

(defmacro defentity [entity-name & rules]
  (d/transact (connect) (mapping-enum  (eval entity-name)))
  (make-var 'curr-entity-name (eval entity-name))
  (let [entity-var (var-get (intern 'core.db.entities (eval entity-name)
                                    {:type (eval entity-name)
                                     :mapping (do (make-var 'flag false)
                                                  (apply merge (mapv #(eval %) rules)))
                                     :rev-mapping (do (make-var 'flag true)
                                                      (apply merge (mapv #(eval %) rules)))}))]
    (when (:mapping-inference mapping-opts)
      (let [prop-map (apply merge (mapv (fn [k] {k [(:type entity-var)]}) (-> entity-var :mapping (keys))))]
        (alter-var-root #'entities-frequencies (fn [o] (merge-with concat-into entities-frequencies prop-map))))))
  (del-var 'curr-entity-name)
  (del-var 'flag)
  identity)

(defmacro defschema [opts & defentities]
  (let [options (eval opts)]
    (alter-var-root #'mapping-opts (fn [o] options)))
  (initialize-database)
  (d/transact (connect) [ENTITY_TYPE_ATTRIB])
  (eval defentities)
  (when (:auto-persist-schema mapping-opts)
    (persist-schema)
    identity))

(defn mapping-by-symbol [symbol]
  (-> symbol resolve var-get))

(defn find-mapping [service-entity]
  (if (reverse-mapping? service-entity)
    (->> (name (:entity/type service-entity)) (str "core.db.entities/") symbol (var-by-symbol))
    (let [freqs (->> (mapv #(get entities-frequencies %) (keys service-entity))
                     (apply concat)
                     (frequencies))
          freqs-vec (mapv (fn [k] {:k k, :v (get freqs k)}) (keys freqs))
          max-val (apply max (mapv #(get freqs %) (keys freqs)))
          max-entries (filter #(= max-val (:v %)) freqs-vec)]
      (when (< 1 (count max-entries)) (throw (ex-info "Cannot determine mapping. At least two mappings with same frequency" {:entries max-entries})))
      (->> (first max-entries)
           :k
           name
           (str "core.db.entities/")
           symbol
           (var-by-symbol)))))

(defn- entity? [entity]
  (or (isa? (type entity) clojure.lang.PersistentVector)
      (isa? (type entity) clojure.lang.PersistentArrayMap)))

(defn reverse-mapping? [entity]
  (if (isa? (type entity) clojure.lang.PersistentVector)
    (let [entry (first entity)] (reverse-mapping? entry))
    (contains? entity :entity/type)))


(defmulti apply-mapping-opts :opt-key)

(defmethod apply-mapping-opts :lookup-ref [{:keys [type func opt-key opt-value source target-property property-value]}]
  (make-var 'do-mapping? false)
  (swap! source assoc target-property (opt-value property-value)))

(defmethod apply-mapping-opts :ref-type [{:keys [type func opt-key opt-value source target-property property-value]}]
  (make-var 'do-mapping? false)
  (swap! source assoc target-property (func property-value
                                            (var-by-symbol (-> opt-value
                                                               mapping-into-ns)))))

(defmethod apply-mapping-opts :default [{:keys [type func opt-key opt-value source target-property property-value]}]
  (swap! source assoc target-property property-value))

(defn- map-property [mapping-func type from-property to-property source mapping-opts]
  (let [property-value (from-property @source)]
    (make-var 'do-mapping? true)
    (doseq [mapping-opt (keys mapping-opts)]
      (apply-mapping-opts {:type type
                           :func mapping-func
                           :opt-key mapping-opt
                           :opt-value (mapping-opt mapping-opts)
                           :source source
                           :target-property to-property
                           :property-value property-value}))
    (when (get-var 'do-mapping?)
      (if (entity? property-value)
        (swap! source assoc to-property (mapping-func property-value))
        (swap! source assoc to-property property-value)))
    (del-var 'do-mapping?))
  (swap! source dissoc from-property))

(defn- has? [property mapping]
  (when (not (contains? mapping property))
    (throw (ex-info "Mapping error. Source property doesn't contain corresponding mapping rule!"
                    {:property property
                     :mapping mapping}))))

(defn- delete-db-meta [source]
  (swap! source dissoc :db/id :entity/type))

(defn- add-db-meta [source-atom mapping]
  (swap! source-atom assoc :db/id (make-temp-id))
  (swap! source-atom assoc :entity/type (db-mapping-type (:type mapping))))

(defn- do-mapping [source-atom entity-type mapping-rules mapping-func]
  (let [source-props (keys @source-atom)]
    (doall (map #(do (has? % mapping-rules)
                     (map-property mapping-func
                                   entity-type
                                   %
                                   (:to-property (% mapping-rules))
                                   source-atom
                                   (:mapping-opts (% mapping-rules)))) source-props))))

(defmulti clj->db (fn ([source mapping] (type source))
                    ([source] (type source))))

(defmethod clj->db (type {})
  ([source mapping]
   (let [mapping-rules (:mapping mapping)
         temp-source (atom source)]
     (do-mapping temp-source (:type mapping) mapping-rules clj->db)
     (add-db-meta temp-source mapping)
     @temp-source))
  ([source]
   (clj->db source (find-mapping source))))

(defmethod clj->db (type [])
  ([source mapping]
   (mapv #(clj->db % mapping) source))
  ([source]
   (clj->db source (find-mapping (first source)))))

(defmulti db->clj (fn [source] (type source)))

(defmethod db->clj (type {})
  ([source mapping]
   (let [mapping-rules (:rev-mapping mapping)
         temp-source (atom source)]
     (do-mapping temp-source (:type mapping) mapping-rules db->clj)
     (delete-db-meta temp-source)
     @temp-source))
  ([source]
   (db->clj source (find-mapping source))))

(defmethod db->clj (type [])
  ([source mapping]
   (mapv #(db->clj % mapping) source))
  ([source]
   (db->clj source (find-mapping (first source)))))