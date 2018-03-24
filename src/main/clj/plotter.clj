(ns plotter
  "Dummy namespace used for specs and keywords.

  See `plotter.core` for the user facing API."
  {:authors ["Reid \"arrdem\" McKenzie <me@arrdem.com>"]
   :license "https://www.eclipse.org/legal/epl-v10.html"}
  (:require [clojure.spec.alpha :as s]
            [detritus.spec :refer [deftag]]))

(s/def ::coordinates
  #{::polar
    ;; FIXME (arrdem 2018-03-10):
    ;;   Other coordinate systems aren't supported yet.
    #_::spherical
    #_::radial})

(deftag curve
  [name :- string?
   f :- ifn?
   coordinates :- ::coordinates])

(deftag curve+error
  [name :- string?
   f :- ifn?
   δx :- ifn?
   δy :- ifn?
   coordinates :- ::coordinates])

(deftag points
  [coll :- seq?
   coordinates :- ::coordinates])

(s/def ::any-curve
  (s/or :simple-curve ::curve
        #_#_:error-curve ::curve+error
        :points ::points))

(s/def ::curves
  (s/* ::any-curve))

(deftag graph
  [curves :- ::curves])
