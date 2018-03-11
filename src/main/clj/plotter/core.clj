(ns plotter.core
  "Tools for plotting."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:refer-clojure :exclude [compose])
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [detritus :refer [none]]
            [detritus.multi :as m]
            [plotter :as p])
  (:import [java.io File StringReader]))

(defn ->curve
  "Function for constructing plottable curves from functions.

  `f` is a function of one argument, the polar `x`, producing a
  numeric value for the single polar `y` at that point. This does
  require that `f` be a proper function which is single-valued."
  ([f]
   (->curve f :coordinates ::p/polar))
  ([f & {:keys [coordinates] :as kwargs}]
   {:type ::p/curve
    :fn   f
    :name name
    :coordinates coordinates}))

;; FIXME: error bars need a LOT of business logic
#_(defn ->curve+error
  "Function for constructing plottable curves with error bounds.

  `f` is a function of one argument, the polar `x`, to a numeric value
  being the polar `y` at that point. This does require that `f` be a
  proper function which is single-valued."
  [f x-err-f y-err-f & {:keys [coordinates] :as kwargs}]
  {:type ::p/curve+error
   :fn f
   :δx x-err-f
   :δy y-err-f
   :coordinates (or coordinates ::p/polar)})

(defonce ^{:private true} h
  (make-hierarchy))

(defmulti as-curve
  "Function for coercing objects to plottable curves."
  #'m/type
  :hierarchy #'h)

(defmethod as-curve ::p/curve [x] x)
(defmethod as-curve ::p/curve+error [x] x)
(defmethod as-curve clojure.lang.IFn [x]
  (log/warn "Assuming `f` is a single valued polar function!")
  (->curve x))

(defn ->graph
  ([]
   {:type ::p/graph
    :curves []})
  ([{:as options} c & curves]
   {:pre [(= 1 (count (set (map :coordinates (cons c curves)))))]}
   (merge options
          {:type ::graph
           :curves (mapv as-curve (cons c curves))})))

(defn- tmp
  "Creates and returns a new temporary `java.io.File`."

  ([]
   (tmp (name (gensym "tempf_")) ".tmp"))
  ([prefix suffix]
   (File/createTempFile prefix suffix))
  ([prefix suffix directory]
   (File/createTempFile prefix suffix directory)))

(defn compose
  "Graphs may be composed together to produce a ... composite image with
  all the same graphs."

  [& graphs-or-curvables]
  {:type ::p/graph
   :curves (mapcat (fn [o]
                     (if (= ::p/graph (:type o))
                       (:curves o)
                       (as-curve o)))
                   graphs-or-curvables)})

(defmulti as-points
  "Function for converting curves to plottable points.

  Returns a file of points, paired with a descriptor explaining the
  format of the file."
  {:argllists '([curve range])}
  (fn [c r] (m/type c)))

(defmethod as-points ::p/curve [{:keys [fn]} range]
  (let [in-f (tmp "points_" ".txt")]
    ;; Populate a tempfile with points
    (with-open [w (io/writer in-f)]
      (binding [*out* w]
        (doseq [i range]
          (printf "%s, %s\n" i (fn i)))))
    ;; Return a gnuplot plotting directive
    {:path  (.getCanonicalPath in-f)
     :using {'x 1 'y 2}}))

;; FIXME (arrdem 2018-03-10):
;;   This is hard because I don't know if either error function 1) exists 2) is symmetric
(defmethod as-points ::p/curve+error [{:keys [fn]}])

(defn- format-range [min max]
  (cond (and min max)
        (format "[%s:%s]" min max)

        (and min (not max))
        (format "[%s:]" min)

        (and (not min) max)
        (format "[:%s]")

        :else "[]"))

(defn- format-using [{:syms [x y δx δy]}]
  {:pre [(nat-int? x)
         (nat-int? y)
         (or (none δx δy)
             (and δx δy))]}
  (str/join ":" (keep identity [x y δx δy])))

(defn render!
  "Given options and an `IFn`, map the `IFn` over the configured interval, producing a set of
  points, and shelling out to GNUplot to render the points to a visualization."

  [{:keys [min max step
           x-min x-max
           y-min y-max
           title autoscale
           image-format size]
    :or   {autoscale    true
           image-format "png"}
    :as   opts}
   {:keys [curves] :as graph}]
  (let [out-f    (tmp "graph_" (str "." image-format))
        interval (range min max step)
        plots    (map #(as-points % interval) curves)
        ranges   (str/join " "
                           [(format-range x-min x-max)
                            (format-range y-min y-max)])
        in       (str/join "\n"
                           `["#!/usr/bin/env gnuplot"
                             ~@(when autoscale
                                 ["set autoscale"])
                             ~(if image-format
                                (format "set terminal \"%s\"%s"
                                        image-format
                                        (when size (str " size " (str/join ", " size))))
                                "set terminal png")
                             ~(format "set output \"%s\"" (.getCanonicalPath out-f))
                             ~@(when title
                                 [(format "set title \"%s\"" title)])
                             ~(str (format "plot %s " ranges)
                                   (str/join ", "
                                             (map (fn [{:keys [path using] :as plot}]
                                                    (format "\"%s\" using %s"
                                                            path (format-using using)))
                                                  plots)))
                             "quit"])]

    ;; Use gnuplot to actually draw the data file
    (merge
     (sh/sh "gnuplot"
            :in (StringReader. in))
     {:script in
      :graph (.toURI out-f)})))
