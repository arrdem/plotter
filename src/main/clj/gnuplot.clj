(ns gnuplot
  "Dummy namespace used for specs and keywords describing gnuplot.

  See `plotter.core` for the user facing API."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.spec.alpha :as s]))

(s/def ::min number?)
(s/def ::max number?)
(s/def ::step number?)
(s/def ::x-min number?)
(s/def ::x-max number?)
(s/def ::y-min number?)
(s/def ::y-max number?)
(s/def ::size
  (s/and (s/or :both   (s/tuple pos-int? pos-int?)
               :square pos-int?)
         (s/conformer (fn [[tag e]]
                        (if (= :both tag) [:square [e e]] e)))))
(s/def ::title string?)
(s/def ::label string?)
(s/def ::autoscale boolean?)
(s/def ::image-format
  #{"png"
    "svg"
    "wxt"
    "pngcairo"
    "eps"
    "epslatex"})
