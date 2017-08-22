(ns impl.renderers.default
  (:require [core.utils.general :refer [make-js-property]]
            [core.drawables :as d]
            [core.rendering :as r]))

;Some fabric object specific options to pass.
(def RESTRICTED_BEHAVIOUR {:hasRotatingPoint false
                           :lockRotation true
                           :lockScalingX true
                           :lockScalingY true})
(def LOCKED_SCALING       {:lockScalingX true
                           :lockScalingY true})
(def LOCKED_ROTATION      {:lockRotation true})
(def LOCKED_MOVEMENT      {:lockMovementX true
                           :lockMovementY true})
(def NO_DEFAULT_CONTROLS  {:hasControls false
                           :hasBorders false
                           :hasRotatingPoint false})

(def LOCKED (merge LOCKED_MOVEMENT LOCKED_ROTATION LOCKED_SCALING NO_DEFAULT_CONTROLS))

(defn- set
  ([source property value]
   (.set source (clj->js {property value})))
  ([source map]
   (.set source (clj->js map))))


(defonce fabric-property-mapping {:left "left"
                                  :top  "top"
                                  :text "text"
                                  :width  "width"
                                  :height "height"
                                  :origin-x "originX"
                                  :origin-y "originY"
                                  :angle "angle"
                                  :x1 "x1"
                                  :y1 "y1"
                                  :x2 "x2"
                                  :y2 "y2"
                                  :z-index "zIndex"
                                  :border-color  "stroke"
                                  :background-color "fill"
                                  :radius "radius"
                                  :font-family "fontFamily"
                                  :font-weight "fontWeight"
                                  :font-size "fontSize"
                                  :text-align "textAlign"
                                  :color "color"
                                  :border-width "strokeWidth"})

(defn to-fabric-property-map [input-map]
  (apply merge (cons LOCKED (mapv (fn [e] {(keyword (or (e fabric-property-mapping) e)) (e input-map)}) (keys input-map)))))

(defn- property-change-render [drawable rendering-context]
  (let [source  (:data (d/state drawable))
        redraw   (-> rendering-context :redraw-properties)
        drawable-update   (get redraw (:uid drawable))
        update-map (apply merge (mapv (fn [e] {(e (:new (get drawable-update e))) (keys drawable-update)})))]
      (set source (to-fabric-property-map update-map))
      (.setCoords source)
      (r/clear-context {:redraw-properties (:uid drawable)})))

(defn- fabric-create-rendering-state [context drawable create]
  (let [fabric-object (create)]
     (make-js-property fabric-object "refId" (:uid drawable))
     (.add (:canvas context) fabric-object)
     {:data fabric-object}))

; in drawable-state we holds an fabric.js object.
; in context we have serveral properties. One of them is fabric.js canvas reference.
(defn- fabric-destroy-rendering-state [context state]
  (let [canvas (:canvas context)]
    (.remove canvas (:data state))))

;;==========================================================================================================
;; rect rendering
;;==========================================================================================================
(defmethod r/do-render [:fabric :rect] [drawable context]
  (property-change-render drawable context))

(defmethod r/create-rendering-state [:fabric :rect] [drawable context]
  (let [data (to-fabric-property-map (d/model drawable))]
    (fabric-create-rendering-state context drawable (fn [] (js/fabric.Rect. (clj->js data))))))

(defmethod r/destroy-rendering-state [:fabric :rect] [drawable context]
  (fabric-destroy-rendering-state context (d/state drawable)))

;;==========================================================================================================
;; circle rendering
;;==========================================================================================================
(defmethod r/do-render [:fabric :circle] [drawable context]
  (property-change-render drawable context))

(defmethod r/create-rendering-state [:fabric :circle] [drawable context]
  (let [data (to-fabric-property-map (d/model drawable))]
    (fabric-create-rendering-state context drawable (fn [] (js/fabric.Circle. (clj->js data))))))

(defmethod r/destroy-rendering-state [:fabric :circle] [drawable context]
  (fabric-destroy-rendering-state context (d/state drawable)))

;;==========================================================================================================
;; line rendering
;;==========================================================================================================
(defmethod r/do-render [:fabric :line] [drawable rendering-context]
  (property-change-render drawable rendering-context))

(defmethod r/create-rendering-state [:fabric :line] [drawable context]
  (let [data (to-fabric-property-map (d/model drawable))]
    (fabric-create-rendering-state context drawable (fn [] (js/fabric.Line. (clj->js [(:x1 data) (:y1 data) (:x2 data) (:y2 data)]) (clj->js data))))))


(defmethod r/destroy-rendering-state [:fabric :line] [drawable context]
  (fabric-destroy-rendering-state context (d/state drawable)))

;;==========================================================================================================
;; triangle rendering
;;==========================================================================================================
(defmethod r/do-render [:fabric :triangle] [drawable context]
  (property-change-render drawable context))

(defmethod r/create-rendering-state [:fabric :triangle] [drawable context]
  (let [data (to-fabric-property-map (d/model drawable))]
   (fabric-create-rendering-state context drawable (fn [] (js/fabric.Triangle. (clj->js data))))))

(defmethod r/destroy-rendering-state [:fabric :triangle] [drawable context]
  (fabric-destroy-rendering-state context (d/state drawable)))

;;==========================================================================================================
;; text rendering
;;==========================================================================================================
(defmethod r/do-render [:fabric :text] [drawable context]
  (property-change-render drawable context))

(defmethod r/create-rendering-state [:fabric :text] [drawable context]
  (let [data (to-fabric-property-map (d/model drawable))]
   (js/console.log (clj->js data))
   (fabric-create-rendering-state context drawable (fn [] (js/fabric.Text. (:text data) (clj->js data))))))

(defmethod r/destroy-rendering-state [:fabric :text] [drawable context]
  (fabric-destroy-rendering-state context (d/state drawable)))