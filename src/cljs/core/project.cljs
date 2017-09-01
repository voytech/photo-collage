(ns core.project
 (:require [reagent.core :as reagent :refer [atom]]
           [cljsjs.jquery]
           [cljsjs.fabric]
           [cljsjs.rx]
           [core.utils.dom :as dom]
           [core.utils.dnd :as dnd]
           [core.entities :as e]
           [core.tools :as t]
           [core.eventbus :as b]
           [core.rendering :as r]
           [core.drawables :as d]
           [impl.renderers.default :as dd]
           [core.layouts :as layouts]))

(defonce project (atom {}))

(defonce bucket-size 50)

(defonce lookup-cache (atom nil))

(defonce dragging-context (atom nil))

(defonce drawable-buckets (atom {}))

(defn- update-buckets [drawable]
  (doseq [key (get @drawable-buckets (:uid drawable))]
    (swap! drawable-buckets dissoc key))
  (swap! drawable-buckets dissoc (:uid drawable))
  (let [x-s (js/Math.floor (/ (:left drawable) bucket-size))
        y-s (js/Math.floor (/ (:top drawable) bucket-size))
        x-e (+ bucket-size (js/Math.floor (/ (+ (:left drawable) (:width drawable)) bucket-size)))
        y-e (+ bucket-size (js/Math.floor (/ (+ (:top drawable) (:height drawable)) bucket-size)))]
    (doseq [x (range x-s x-e)
            y (range y-s y-e)]
      (let [coord-key (str x "." y)
            drawables (or (get @drawable-buckets coord-key) [])
            keys      (or (get @drawable-buckets (:uid drawable)) [])]
        (swap! drawable-buckets assoc coord-key (cons (:uid drawable) drawables))
        (swap! drawable-buckets assoc (:uid drawable) (cons coord-key keys))))))

(defn- lookup [x y]
  (if (and (not (nil? @lookup-cache)) (d/contains-point? @lookup-cache x y))
      @lookup-cache
      (do
        (reset! lookup-cache nil)
        (let [drawable (first (filter (fn [e] (d/contains-point? e x y)) (vals @d/drawables)))]
         (reset! lookup-cache drawable)))))

(defn- lookup-all [x y]
  (->> @d/drawables
       vals
       (filter #(d/contains-point? % x y))
       (sort-by #(d/getp % :z-index) >)))


(defn- lookup-drawable [x y]
  (let [x-s (js/Math.floor (/ x bucket-size))
        y-s (js/Math.floor (/ y bucket-size))]
    (doseq [drawable (get @drawable-buckets (str x-s "." y-s))]
      (when (and (>= x (d/get-left drawable)) (<= x (+ (d/get-left drawable) (d/get-width drawable)))
                 (>= y (d/get-top drawable)) (<= y (+ (d/get-top drawable) (d/get-height drawable))))
          drawable))))

(defonce event-map {"object:moving" "mousedrag"
                    "mousedown" "mousedown"
                    "mouseup" "mouseup"
                    "click" "mouseclick"
                    "dbclick" "mousedbclick"
                    "mousemove" "mousemove"
                    "mouseenter" "mouseenter"
                    "mouseleave" "mouseleave"})

(defonce source-events "click dbclick mousemove mousedown mouseup mouseenter mouseleave keypress keydown keyup")

(defn- normalise-event-type [event]
  (get event-map event))

(defn- enrich [drawable-id]
  (let [entity             (e/lookup drawable-id :entity)
        component          (e/lookup drawable-id :component)
        attribute-value    (e/lookup drawable-id :attribute)
        drawable           (:drawable component)]
    (merge event {:entity           entity
                  :attribute-value  attribute-value
                  :drawable         drawable
                  :component        component})))

(defn- event-name [decomposed]
   (if (nil? (:entity decomposed))
     (:type decomposed)
     (let [entity-type    (str (name (-> decomposed :entity :type)) ".")
           attribute-type (if (not (nil? (:attribute-value decomposed)))
                              (str (name (-> decomposed :attribute-value :attribute :name)) ".")
                              "")
           component-type (str (name (-> decomposed :component :type)) ".")]
        (str entity-type attribute-type component-type (:type decomposed)))))


(defn normalise-event [e obj]
  (let [rect (.getBoundingClientRect obj)
        left (- (.-clientX e) (.-left rect))
        top (- (.-clientY e) (.-top rect))]
     {:source e
      :state nil
      :ctrl-key (.-ctrlKey e)
      :target (.-target e)
      :type (normalise-event-type (.-type e))
      :left left
      :top  top
      :movement-x 0
      :movement-y 0}))


(defn- set-type [prev curr]
  (if (and (= "dragging" (:state prev)) (= "mousemove" (:type curr))) "mousedrag" (:type curr)))

(defn- set-state [prev curr]
   (cond
     (= "mousedown" (:type curr)) "dragging"
     (= "mouseup"   (:type curr)) "moving"
     :else (:state prev)))

(defn- get-dragging-context [event]
  (when (= "dragging" (:state event))
    @dragging-context))

(defn- update-dragging-context [event]
  (reset! dragging-context (if (= "dragging" (:state event)) (:drawable event) nil)))

(defn- update-stroke [prev curr]
  (let [current (or (:stroke prev) "")
        arr (clojure.string/split current #"\.")
        trimmed (if (> (count arr) 9) (clojure.string/join "." (take-last 9 arr)) current)]
     (str trimmed "." (:type curr))))

(defn- detect-pattern [prev curr])

(defn- merge-streams [obj events]
  (apply js/Rx.Observable.merge (mapv (fn [e] (js/Rx.Observable.fromEvent obj e)) events)))

(defn- delta-stream [input func]
  (.scan input (fn [acc,e] (merge acc e (func acc e))) {}))

(defn- enriching-stream [input]
  (.map input (fn [e] (->> (enrich (or (get-dragging-context e) (first (lookup-all (:left e) (:top e)))))
                           (merge e)))))

(defn- mouse-out? [prev curr]
  (and (= "mousemove" (:type prev)) (not (nil? (:entity prev))) (nil? (:entity curr))))

(defn- dispatch-events [id events patterns]
  (let [obj (js/document.getElementById id)
        stream (merge-streams obj events)
        normalized (.map stream (fn [e] (normalise-event e obj)))
        delta    (delta-stream normalized (fn [acc e] {:movement-x (- (:left e) (or (:left acc) 0))
                                                       :movement-y (- (:top e) (or (:top acc) 0))
                                                       :type  (set-type acc e)
                                                       :stroke (update-stroke acc e)
                                                       :state (set-state acc e)}))
        enriched (enriching-stream delta)
        last     (delta-stream enriched (fn [acc e]
                                          (let [mouse-out (mouse-out? acc e)]
                                           {:type (if mouse-out "mouseout" (:type e))
                                            :entity (if mouse-out (:entity acc) (:entity e))
                                            :drawable (if mouse-out (:drawable acc) (:drawable e))
                                            :component (if mouse-out (:component acc) (:component e))
                                            :attribute-value (if mouse-out (:attribute-value acc) (:attribute-value e))})))]
    (.subscribe last    (fn [e]
                            (when-not (nil? (:entity e))
                              (update-dragging-context e))
                            (js/console.log (str "on " (event-name e)))
                            (b/fire (event-name e) e)))))

(defn initialize [id {:keys [width height]}]
  (dom/console-log (str "Initializing canvas with id [ " id " ]."))
  (let [data {:canvas (js/fabric.StaticCanvas. id)
              :id id
              :width width
              :height height}]
    (.setWidth (:canvas data) width)
    (.setHeight (:canvas data) height)
    (reset! project data)
    (dispatch-events id (clojure.string/split source-events #" ") [])
    (b/fire "rendering.context.update" {:canvas (:canvas data)})))
  ;;(let [canvas (:canvas (proj-page-by-id id))]
  ;;  (do (.setWidth canvas @zoom-page-width)
  ;;      (.setHeight canvas @zoom-page-height)
  ;;  (.setZoom canvas @zoom))

;;--------------------------------
;; API dnd event handling with dispatching on transfer type
;;---------------------------------

;TODO how should we handle dragNdrop events originating from particulaar tool? If not all tools produces entities - some can have different behaviour in canvas context
; For example : attribute value producing tool will bind attrib value to entity. It in fact can just return entity to which attribute value was added
; then this entity is going to be synchronized - all changes made are going to be propageted to canvas.

(defmethod dnd/dispatch-drop-event "tool-data" [event]
  (let [tool-id (dnd/get-dnd-data event "tool-data")
        context (dnd/event-layer-coords event)
        tool-obj (t/by-id tool-id)]
    (t/invoke-tool tool-obj context)))
