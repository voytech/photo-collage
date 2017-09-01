(ns core.rendering
  (:require [core.eventbus :as bus]
            [core.entities :as e]
            [core.drawables :as d]
            [core.layouts :as l]))

(declare render)

; Sets default renderers
(def RENDERER (atom :fabric))

; Sets default rendering options.
; Options can be :
; :auto - should rendering be triggered automatically in drawable model property  changes or only on 'rendering.execute' events ?
(def OPTIONS (atom {:auto false}))

(defonce rendering-context (atom {}))

(defn set-rendering [renderer]
  (reset! RENDERER renderer))

(defn get-rendering []
  @RENDERER)

(defn update-context [value-map]
  (reset! rendering-context (merge value-map @rendering-context)))

(defn clear-context [path]
  (swap! rendering-context update-in (drop-last path) dissoc (last path)))

(defn assoc-context [path value]
  (swap! rendering-context assoc-in path value))

(defn- render-components [components]
  (doseq [component components]
     (let [drawable (:drawable component)]
       (render drawable))))

(defn- render-entity [entity]
  (render-components  (e/components entity))
  (doseq [attribute-value (:attributes entity)]
    (render-components (e/components attribute-value)))
  (let [bbox (l/get-bbox entity)
        cbox {:left (+ (:left (e/get-entity-content-bbox entity)) (:left bbox))
              :top  (+ (:top (e/get-entity-content-bbox entity)) (:top bbox))
              :width (:width (e/get-entity-content-bbox entity))
              :height (:height (e/get-entity-content-bbox entity))}]
    (l/layout cbox (e/get-attributes-values entity))
    (bus/fire "rendering.finish")))

(bus/on ["rendering.context.update"] -999 (fn [event]
                                            (let [context (:context @event)]
                                              (js/console.log "rendering.context.update handled")
                                              (update-context context)
                                              (js/console.log (clj->js @rendering-context)))))

(bus/on ["drawable.created"] -999 (fn [event]
                                    (let [context (:context @event)
                                          drawable (:drawable context)])))


(bus/on ["drawable.added"] -999 (fn [event]))

(defn- update-property-to-redraw [drawable property newvalue oldvalue]
  (let [properties (conj (or (get-in @rendering-context [:redraw-properties (:uid drawable)]) #{}) property)]
    (swap! rendering-context assoc-in [:redraw-properties (:uid drawable)] properties))) ;{:new newvalue :old oldvalue}))

(bus/on ["drawable.changed"] -999 (fn [event]
                                    (let [context (:context @event)
                                          drawable (:drawable context)]
                                       (update-property-to-redraw drawable (:property context) (:new context) (:old context)))))

(bus/on ["drawable.render" "drawable.layout.finished"] -999 (fn [event]
                                                              (let [context (:context @event)
                                                                    drawable (:drawable context)]
                                                                 (render drawable))))

(bus/on ["drawable.removed"] -999 (fn [event]
                                    (let [context (:context @event)
                                          drawable (:drawable context)]
                                       (destroy-rendering-state drawable @rendering-context))))

(bus/on ["entities.render"] -999 (fn [event]
                                     (let [context (:context @event)
                                           entities  (:entities context)]
                                        (doseq [entity enttities] (render-entity entity)))))

(bus/on ["entity.added"] -999 (fn [event]
                                 (let [context (:context @event)])))
                                    ;(render-entity (:entity context)))))

(bus/on ["entity.render"] -999 (fn [event]
                                 (let [context (:context @event)]
                                    (js/console.log "entity.render fired.")
                                    (js/console.log (clj->js (:entity context)))
                                    (render-entity (:entity context)))))

(bus/on ["uncommited.render"] -999 (fn [event]
                                     (let [uncommited (get @rendering-context :redraw-properties)]
                                       (doseq [drawable-id (keys uncommited)]
                                          (render (get @d/drawables drawable-id))))))


(defmulti do-render (fn [drawable context] [@RENDERER (:type drawable)]))

(defmulti create-rendering-state (fn [drawable context] [@RENDERER (:type drawable)]))

(defmethod create-rendering-state :default [drawable context])

(defmulti destroy-rendering-state (fn [drawable context] [@RENDERER (:type drawable)]))

(defmethod destroy-rendering-state :default [rendering-state context])

(defn- rewrite-redraw-properties [drawable]
  (when (not (nil? (:redraw-properties @rendering-context)))
    (let [redraw   (get-in @rendering-context [:redraw-properties (:uid drawable)])
          update-map (apply merge (mapv (fn [e] {e (:new (get redraw e))}) (keys redraw)))]
      (assoc-context [:redraw-properties (:uid drawable)] update-map))))

(defn render [drawable]
  (when (not (nil? drawable))
    ;(js/console.log (str ">>>[ " (:type drawable) " " (:uid drawable)  " ]>>>"))
    (let [rendering-state (d/state drawable)]
      (when (or (nil? rendering-state) (empty? rendering-state))
        (d/update-state drawable (create-rendering-state drawable @rendering-context)))
      ;(rewrite-redraw-properties drawable)
      ;(js/console.log (apply str  (cons "Rendering properties: " (get-in @rendering-context [:redraw-properties (:uid drawable)]))))
      (do-render drawable @rendering-context)
      (clear-context [:redraw-properties (:uid drawable)]))))
      ;(js/console.log (str "<<<[ " (:type drawable) " " (:uid drawable)  " ]<<<\n")))))
