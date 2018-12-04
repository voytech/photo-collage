(ns ui.components.workspace
  (:require [reagent.core :as reagent :refer [atom]]
            [core.utils.dnd :as dnd]
            [ui.components.generic.basic :as cmp :refer [DynamicPagination]]
            [core.project :as p :refer [project]]
            [impl.synthetic-events :as s]
            [core.tools :as t :refer [tools]]
            [impl.synthetic-events :as patterns]))


(defn resolve-drop "Should resolve drop events for following scenarios:
                    1. The drag source is from the library component (image is dragged)
                    2. The drag source comes from desktop (Use FileApi to load image)"
  [state]
  (fn [event]
    (let [e (.-nativeEvent event)]
      (.preventDefault e)
      (dnd/dispatch-drop-event e state))))

(defn app-config [width height renderer]
  {:width width
   :height height
   :renderer renderer
   :events {
     :canonical-events {
       "mousedown"  "mouse-down"
       "mouseup"    "mouse-up"
       "click"      "mouse-click"
       "dbclick"    "mouse-db-click"
       "mousemove"  "mouse-move"
       "mouseenter" "mouse-enter"
       "mouseleave" "mouse-leave"
     }
     :application-events {
       "mouse-dragging-move"   "move"
       "mouse-in"              "focus"
       "mouse-out"             "blur"
       "mouse-point-click"     "activate"
     }
     :patterns {
       :mouse-dragging-move (patterns/mouse-dragging-move-event)
       :mouse-move (patterns/mouse-move-event)
       :mouse-in (patterns/mouse-in-event)
       :mouse-out (patterns/mouse-out-event)
       :mouse-point-click (patterns/mouse-click-event)
     }
   }})

(defn canvas-initializing-wrapper [app-state width height renderer]
 (with-meta identity
   {:component-did-mount (fn [el]
                            (let [domid (.-id (reagent/dom-node el))]
                              (p/initialize domid app-state width height renderer)))}))

(defn Workspace [class]
  (let [config (app-config 1270 1000 :reagentsvg)
        app-state (atom {:dom {:id "project" :width 1270 :height 1000}
                         :events (:events config)
                         :diagram {:entities {}}})]
                         
    [:div {:id "workspace-inner" :class (:class class)}
      [:div {:id "canvas-wrapper"
             :class "workspace-div"
             :on-drop (resolve-drop app-state)
             :on-drag-over #(.preventDefault %)}
         [(canvas-initializing-wrapper app-state 1270 1000 :reagentsvg)
           [:div {:id "project" :class "canvas"}]]]]))
