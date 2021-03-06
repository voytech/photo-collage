(ns cljs-diagrams.impl.diagrams.bpmn.shapes
  (:require [cljs-diagrams.core.shapes :as d :refer [layout-attributes]]
            [cljs-diagrams.impl.layouts.expression :as w :refer [layout-hints]]
            [cljs-diagrams.core.funcreg :refer [serialize]]
            [cljs-diagrams.core.layouts :as l :refer [layout
                                                      weighted-position
                                                      weighted-size
                                                      weighted-origin
                                                      match-parent-size
                                                      match-parent-position
                                                      margins]])
  (:require-macros [cljs-diagrams.core.macros :refer [defshape defshapes-group shape defp]]))

(defp diamond-bbox-draw []
  (fn [c]
    (let [top-x    (+ (d/get-left c) (/ (d/get-width c)  2))
          top-y    (d/get-top c)
          right-x  (+ (d/get-left c) (d/get-width c))
          right-y  (+ (d/get-top c) (/ (d/get-height c)  2))
          bottom-x (+ (d/get-left c) (/ (d/get-width c)  2))
          bottom-y (+ (d/get-top c) (d/get-height c))
          left-x   (d/get-left c)
          left-y   (+ (d/get-top c) (/ (d/get-height c)  2))]
      {:points [top-x top-y right-x right-y bottom-x bottom-y left-x left-y top-x top-y]})))

(defn diamond-initializer []
  (fn [container props]
    (let [top-x (/ (-> container :bbox :width) 2)
          top-y 0
          right-x
          right-y
          bottom-x
          bottom-y
          left-x
          left-y]
      {:points  [top-x top-y right-x right-y bottom-x bottom-y left-x left-y]
       :left 0
       :top 0
       :border-color "black"
       :border-style "solid"
       :border-width 1
       :background-color "white"
       :z-index 0})))

(defshape diamond {:rendering-method :draw-poly-line
                   :model-customizers [(d/bbox-draw diamond-bbox-draw)]
                   :initializer (diamond-initializer)})