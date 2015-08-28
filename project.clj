(defproject cljds/ch6 "0.1.0"
  :description "Example code for the book Clojure for Data Science"
  :url "https://github.com/clojuredatascience/ch6-clustering"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clojure-opennlp "0.3.3"]
                 [org.apache.mahout/mahout-core "0.9" :exclusions [org.apache.hadoop/hadoop-core]]
                 [org.apache.mahout/mahout-integration "0.9"]
                                  
                 [stemmers "0.2.2"]
                 [org.apache.lucene/lucene-benchmark "4.10.3"]
                 [incanter "1.5.5"]
                 [org.clojure/math.combinatorics "0.0.8"]

                 [cc.mallet/mallet "2.0.7"]

                 [me.raynes/fs "1.4.6"]

                 [com.damballa/parkour "0.6.2"]
                 [com.damballa/abracad "0.4.12"]
                 [org.apache.hadoop/hadoop-client "2.7.0"]
                 [org.apache.hadoop/hadoop-common "2.7.0"]
                 [org.apache.hadoop/hadoop-hdfs "2.7.0"]
                 
                 [org.apache.avro/avro-mapred "1.7.7" :classifier "hadoop2"]
                 [org.slf4j/slf4j-api "1.6.1"]
                 [org.slf4j/slf4j-log4j12 "1.6.1"]
                 [log4j "1.2.17"]]
  :aliases {"extract-reuters" ["run" "-e" "6.0"]
            "create-sequencefile" ["run" "-e" "6.17"]
            "create-vectors" ["run" "-e" "6.18"]}
  :profiles
  {:dev
   {:dependencies [[org.clojure/tools.cli "0.3.1"]]
    :repl-opts {:init-ns cljds.ch6.examples}
    :resource-paths ["dev-resources"
                     "data/reuters-text"]}}
  :main cljds.ch6.core
  :aot [clojure.core.reducers cljds.ch6.core]
  :jvm-opts ["-Xmx2g"])

