(ns plotter.core
  "Tools for plotting."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:refer-clojure :exclude [compose])
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [detritus.multi :as m])
  (:import [java.io File StringReader]))

(defn ->curve
  ([f]
   (fn->curve f {}))
  ([f {:keys [name] :as options}]
   {:type ::curve
    :fn   f
    :name name}))

(defonce ^{:private true} h
  (make-hierarchy))

(defmulti as-curve
  ""
  {}
  #'dx-1
  :hierarchy #'h)

(defmethod as-curve ::curve [x] x)
(defmethod as-curve clojure.lang.IFn [x]
  (->curve x))

(defn ->graph
  ([]
   {:type ::graph
    :curves []})
  ([{:as options} c & curves]
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
  "Graphs may be composed together to produce"

  [g & graphs]
  )

(defn render!
  "Given options and an `IFn`, map the `IFn` over the configured interval, producing a set of
  points, and shelling out to GNUplot to render the points to a visualization."

  [{:keys [range/min range/max range/step title] :as opts} f]
  (let [in-f  (tmp "points_" ".txt")
        out-f (tmp "graph_" ".png")
        title (or title (str f))
        in    (format "#!/usr/bin/env gnuplot
set autoscale
set terminal png
set output \"%s\"
set title \"%s\"
plot [%s:%s] \"%s\"
quit"
                      (.getCanonicalPath out-f)
                      title
                      min max
                      (.getCanonicalPath in-f))]

    (with-open [w (io/writer in-f)]
      (binding [*out* w]
        (doseq [i (range min max step)]
          (printf "%s, %s\n" i (f i)))))

    ;; Use gnuplot to actually draw the data file
    (merge
     (sh/sh "/usr/local/bin/gnuplot"
            :in (StringReader. in))
     {:points (.toURI in-f)
      :graph  (.toURI out-f)})))
