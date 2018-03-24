(ns plotter.core
  "Tools for plotting."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"],
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [detritus :refer [none]]
            [detritus.multi :as m]
            [gnuplot :as g]
            [plotter :as p])
  (:import [java.io File StringReader]))

(s/fdef ->curve
        :args (s/or :1 ifn?
                    :& (s/cat :f ifn?
                              :kwargs (s/keys* :opt-un [::p/coordinates
                                                        ::p/title])))
        :ret ::p/curve)

(defn ->curve
  "Function for constructing plottable curves from functions.

  `f` is a function of one argument, the polar `x`, producing a
  numeric value for the single polar `y` at that point. This does
  require that `f` be a proper function which is single-valued.

  Supported options:
  - `:coordinates`, at present only `::p/polar` is supported
  - `:title`, an optional string which will be used as this curve's label"
  ([f]
   {:type ::p/curve, :fn f, :coordinates ::p/polar})
  ([f & {:as kwargs}]
   (merge (->curve f) kwargs)))

(s/fdef ->points
        :args (s/or :1 coll?
                    :& (s/cat :coll coll?
                              :kwargs (s/keys* :opt-un [::p/coordinates
                                                        ::p/title]))))

(defn ->points
  "Function for constructing plottable curves given data sets of points
  as a collection of polar `[x y]` pairs with no error component.

  Supported options:
  - `:coordinates`, at present only `::p/polar` is supported
  - `:title`, an optional string which will be used as this curve's label"
  ([coll]
   {:type ::p/points
    :coordinates ::p/polar
    :coll coll})
  ([coll & {:as kwargs}]
   (merge (->points coll) kwargs)))

;; FIXME: error bars need a LOT of business logic
#_(s/fdef ->curve+error
          :args (s/or :1 ifn?
                      :& (s/cat :f ifn?
                                :opts map?
                                :kwargs (s/keys* :opt-un [::p/coordinates])))
          :ret ::p/curve)

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

(s/fdef as-curve
        :args any?
        :ret ::p/curve)

(defmulti as-curve
  "Function for coercing objects to plottable curves.

  Supports functions which are assumed to be unary, representing a
  polar plot. Does not transform objects which are already curves."
  #'m/type
  :hierarchy #'h)

(defmethod as-curve ::p/curve [x] x)
(defmethod as-curve ::p/points [x] x)
(defmethod as-curve ::p/curve+error [x] x)
(defmethod as-curve clojure.lang.IFn [x]
  (log/warn "Assuming `f` is a single valued polar function!")
  (->curve x))

(s/fdef ->graph
        :args (s/or :0 (s/cat)
                    :2+ (s/cat :opts map?
                               :curves (s/+ ::p/curve)))
        :ret ::p/graph)

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

(s/fdef compose
        :args (s/* (s/or :graph ::p/graph
                         :curve ::p/curve
                         :ifn   ifn?))
        :ret ::p/graph)

(defn compose
  "Function for \"composing\" together a zero or more \"plottables\" -
  being either graphs, curves or objects which can be coerced to a
  curve via `#'as-curve`.

  Returns a graph containing all the plottables, which must all be in
  the same coordinate system."

  [& graphs-or-curvables]
  {:post [(= 1 (count (set (map :coordinates (:curves %)))))]}
  {:type ::p/graph
   :curves (->> graphs-or-curvables
                (mapcat (fn [o]
                          (if (= ::p/graph (:type o))
                            (:curves o)
                            [(as-curve o)])))
                (into []))})

(s/fdef as-points
        :args (s/cat :curve ::p/any-curve
                     :range seq?)
        :ret (s/keys :path string?
                     :using (s/map-of symbol? pos-int?)))

(defmulti as-datafile
  "Function for converting a curve and an interval to plottable points.

  Returns a file of points, paired with a descriptor explaining the
  format of the file."
  {:arglists '([curve range])}
  (fn [c r] (m/type c)))

(defmethod as-datafile ::p/curve [{:keys [fn] :as c} range]
  (let [in-f (tmp "points_" ".txt")]
    ;; Populate a tempfile with points
    (with-open [w (io/writer in-f)]
      (binding [*out* w]
        (doseq [i range]
          (printf "%s, %s\n" i (fn i)))))
    ;; Return a gnuplot plotting directive
    (merge c
           {:type ::datafile
            :path (.getCanonicalPath in-f)
            :using {'x 1 'y 2}})))

(defmethod as-datafile ::p/points [{:keys [coll] :as c} range]
  ;; We ignore the range and let gnuplot sort it out
  (let [in-f (tmp "points_" ".txt")]
    ;; Populate a tempfile with points
    (with-open [w (io/writer in-f)]
      (binding [*out* w]
        (doseq [[x y] coll]
          (printf "%s, %s\n" x y))))
    ;; Return a gnuplot plotting directive
    (merge c
           {:type ::datafile
            :path  (.getCanonicalPath in-f)
            :using {'x 1 'y 2}})))

;; FIXME (arrdem 2018-03-10):
;;   This is hard because I don't know if either error function 1) exists 2) is symmetric
(defmethod as-datafile ::p/curve+error [{:keys [fn]}])

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

(defmacro ^:private line-template [& exprs]
  `(->> [~@exprs]
        (reduce (fn [acc# e#]
                  (cond (nil? e#) acc#
                        (seq? e#) (into acc# e#)
                        :else     (conj acc# e#)))
                [])
        (str/join "\n")))

(s/def ::out string?)
(s/def ::err string?)
(s/def ::exit integer?)
(s/def ::script string?)
(s/def ::graph uri?)

(s/fdef render!
        :args (s/cat :curve ::p/curve
                     :opts (s/keys* :opt-un [::g/min ::g/max ::g/step
                                             ::g/x-min ::g/x-max
                                             ::g/y-min ::g/y-max
                                             ::g/title
                                             ::g/autoscale
                                             ::g/image-format
                                             ::g/size]))
        :ret (s/keys :req-un [::out ::err ::exit ::script ::graph]))

(defn render!
  "Given a graph and optional keyword arguments, render the curves
  constituting the graph to points and plot the points via gnuplot.

  Supported options:
  - `:min` the smallest (and first) x value to be plotted (default 0)
  - `:max` the largest x value to be plotted (default 100)
  - `:step` the interval on x at which to plot function points (default 0.1)
  - `:autoscale` (true by default) tells gnuplot to size the graph
  - `:size` either a single integer denoting a square image, or a pair
     denoting the rectangular dimensions of the image.
  - `:{x,y}-{min,max}` specify limits on the dimensions of the graph
  - `:title` sets the graph's title
  - `:image-format` (png default )tells gnuplot what kind of image to produce."

  [graph & {:as kwargs}]
  (let [{:keys [curves] :as graph}   graph
        {:keys [min max step
                x-min x-max
                y-min y-max
                title
                autoscale
                image-format size]
         :or   {min          0,
                max          100,
                step         0.1
                autoscale    true
                image-format "png"}} kwargs

        out-f    (tmp "graph_" (str "." image-format))
        interval (range min max step)
        plots    (map #(as-datafile % interval) curves)
        ranges   (str/join " "
                           [(format-range x-min x-max)
                            (format-range y-min y-max)])
        in       (line-template
                  "#!/usr/bin/env gnuplot"
                  (when autoscale "set autoscale")
                  (format "set terminal \"%s\"%s"
                          image-format
                          (if size
                            (cond (and (vector? size)
                                       (= 2 (count size))
                                       (every? pos-int? size))
                                  (str " size " (str/join ", " size))

                                  (pos-int? size)
                                  (format " size %s, %s" size size))
                            ""))
                  (format "set output \"%s\"" (.getCanonicalPath out-f))
                  (when title
                    (format "set title \"%s\"" title))
                  (str (format "plot %s " ranges)
                       (str/join ", "
                                 (map (fn [{:keys [path using title] :as plot}]
                                        (format "\"%s\" using %s%s"
                                                path (format-using using)
                                                (format " title \"%s\"" (or title path))))
                                      plots)))
                  "quit")]

    ;; Use gnuplot to actually draw the data file
    (merge
     (sh/sh "gnuplot"
            :in (StringReader. in))
     {:type   ::p/render
      :script in
      :graph  (.toURI out-f)})))
