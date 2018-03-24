# Plotter
<a href="https://de.wikipedia.org/wiki/Plotter#/media/File:Hp_9862a.jpg"><img align="right" src="/etc/plotter.jpg" width=300/></href>

[![Clojars Project](http://clojars.org/me.arrdem/plotter/latest-version.svg)](https://clojars.org/me.arrdem/plotter)

> Use a picture. It's worth a thousand words.
>
> -- "Speakers Give Sound Advice". Syracuse Post Standard. page 18. March 28, 1911.

Graphs are a great way to summarize lots of data for rapid consumption by humans.
Unfortunately, graphs aren't quite data, in that they're most useful when they are lossy representations.

Plotter is a simple toolkit which attempts to keep graphs in a composable data-first representation for as long as possible, enabling them to be listed for simultaneous display in a graph and ultimately rendered to a graphic.

## Demo

```clj
user> (require '[plotter.core :refer :all])
nil
user> (def sin (->curve #(Math/sin %) :title "sin(x)"))
#'user/sin
user> (def cos (->curve #(Math/cos %) :title "cos(x)"))
#'user/cos
user> (render! (compose sin cos)
         :min 0
         :max (* 4 Math/PI)
         :step 0.1
         :y-min -2
         :y-max 2
         :size 500
         )
{:exit 0,
 :out "",
 :err "",
 :script "#!/usr/bin/env gnuplot
set autoscale
set terminal \"png\" size 3000, 3000
set output \"/tmp/graph_251417161639590865.png\"
plot [] [-2:2] \"/tmp/points_1074626684104989046.txt\" using 1:2 title \"sin(x)\", \"/tmp/points_717203087347687554.txt\" using 1:2 title \"cos(x)\"
quit",
 :graph #object[java.net.URI "0x1c754b93" "file:/tmp/graph_251417161639590865.png"]}
user>
```

<center><img src="/etc/sin-cos.png" alt="sin, cos demo"/></center>

## API Overview

### [plotter.core/->curve](/src/main/clj/plotter/core.clj#L23)
 - `(->curve f)`
 - `(->curve f & {:as kwargs})`

Function for constructing plottable curves from functions.

`f` is a function of one argument, the polar `x`, producing a
numeric value for the single polar `y` at that point. This does
require that `f` be a proper function which is single-valued.

Supported options:
- `:coordinates`, at present only `::p/polar` is supported
- `:title`, an optional string which will be used as this curve's label

### [plotter.core/->points](/src/main/clj/plotter/core.clj#L44)
 - `(->points coll)`
 - `(->points coll & {:as kwargs})`

Function for constructing plottable curves given data sets of points
as a collection of polar `[x y]` pairs with no error component.

Supported options:
- `:coordinates`, at present only `::p/polar` is supported
- `:title`, an optional string which will be used as this curve's label

### [plotter.core/compose](/src/main/clj/plotter/core.clj#L133)
 - `(compose & graphs-or-curvables)`

Function for "composing" together a zero or more "plottables" -
being either graphs, curves or objects which can be coerced to a
curve via [`#'plotter.core/as-curve`](/README.md#plottercoreas-curve).

Returns a graph containing all the plottables, which must all be in
the same coordinate system.

### [plotter.core/as-curve](/src/main/clj/plotter/core.clj#L86)

Function for coercing objects to plottable curves.

Supports functions which are assumed to be unary, representing a
polar plot. Does not transform objects which are already curves.

### [plotter.core/render!](/src/main/clj/plotter/core.clj#L241)
 - `(render! graph & {:as kwargs})`

Given a graph and optional keyword arguments, render the curves
constituting the graph to points and plot the points via gnuplot.

Supported options:
- `:autoscale` (true by default) tells gnuplot to size the graph
- `:size` either a single integer denoting a square image, or a pair
   denoting the rectangular dimensions of the image.
- `:{x,y}-{min,max}` specify limits on the dimensions of the graph
- `:title` sets the graph's title
- `:image-format` (png default )tells gnuplot what kind of image to produce.

## License

Copyright (C) 2018 Reid "arrdem" McKenzie

Distributed under the [Eclipse Public License](/LICENSE) either version 1.0 or (at your option) any later version.
