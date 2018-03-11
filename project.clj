(defproject me.arrdem/plotter "_"
  :description "FIXME"
  :url "http://github.com/arrdem/plotter"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/spec.alpha "0.1.143"]]
  
  :source-paths      ["src/main/clj"
                      "src/main/cljc"]
  :java-source-paths ["src/main/jvm"]
  :resource-paths    ["src/main/resources"]

  :profiles
  {:test
   {:test-paths        ["src/test/clj"
                        "src/test/cljc"]
    :java-source-paths ["src/test/jvm"]
    :resource-paths    ["src/test/resources"]}
   :dev
   {:source-paths      ["src/dev/clj"
                        "src/dev/cljc"]
    :java-source-paths ["src/dev/jvm"]
    :resource-paths    ["src/dev/resources"]}}

  :plugins [[me.arrdem/lein-git-version "2.0.5"]]

  :git-version
  {:status-to-version
   (fn [{:keys [tag version ahead ahead? dirty?] :as git}]
     (if (and tag (not ahead?) (not dirty?))
       tag
       (if tag
         (str tag
              (when ahead? (str "." ahead))
              (when dirty? "-SNAPSHOT"))
         "0.1.0-SNAPSHOT")))})
