(ns cljds.ch6.evaluation
  (:require [parkour.io
             [seqf :as seqf]]
            [parkour.wrapper :refer [Wrapper]]
            [clojure.core.reducers :as r]
            [incanter.core :as i]
            [incanter.stats :as s]
            [incanter.charts :as c]
            [stemmers.core :as stemmer]
            [clojure.math.combinatorics :refer [combinations]])
  (:import [org.apache.hadoop.conf Configuration]
           [org.apache.hadoop.fs FileSystem Path]
           [org.apache.hadoop.io SequenceFile SequenceFile$Writer IntWritable Text SequenceFile$Reader]
           [org.apache.mahout.math DenseVector VectorWritable]
           [org.apache.mahout.common.distance DistanceMeasure EuclideanDistanceMeasure CosineDistanceMeasure]
           [org.apache.mahout.clustering Cluster]
           [org.apache.mahout.clustering.kmeans KMeansDriver Kluster]
           [org.apache.mahout.clustering.iterator ClusterWritable]
           [org.apache.mahout.clustering.classify WeightedVectorWritable WeightedPropertyVectorWritable]
           [org.apache.mahout.clustering.streaming.mapreduce CentroidWritable]))

(defn centroids-path [dir]
  (str dir "/clusters-*-final"))

(defn points-path [dir]
  (str dir "/clusteredPoints"))

(defn name->document [s]
  (let [id (Long/parseLong s)
        n1 (quot id 1000)
        n2 (rem  id 1000)]
    (format "/reut2-%03d.sgm-%d.txt" n1 n2)))

(defn vector->document [vector]
  (->> (.getName vector)
       (name->document)
       (str "data/reuters-text/")
       (slurp)))

(defn cluster-top-terms [cluster]
  (->> (mapcat (comp stemmer/stems vector->document) cluster)
       (frequencies)
       (sort-by second >)
       (take 20))) 

(defn cluster-documents [vectors]
  (map (comp name->document #(.getName %)) vectors))

(defn load-cluster-points [dir]
  (->> (points-path dir)
       (seqf/dseq)
       (r/reduce
        (fn [accum [k v]]
          (update-in accum [k] conj v)) {})))

(defn load-cluster-centroids [dir]
  (let [to-tuple (fn [^Cluster kluster]
                   (let [id (.getId kluster)]
                     [id  {:id id
                           :centroid (.getCenter kluster)}]))]
    (->> (centroids-path dir)
         (seqf/dseq)
         (r/map (comp to-tuple last))
         (into {}))))

(def measure
  (CosineDistanceMeasure.))

(defn distance [^DistanceMeasure measure a b]
  (.distance measure a b))

(defn avg [xs]
  (/ (reduce + xs)
     (count xs)))

(defn assoc-points [cluster points]
  (assoc cluster :points points))

(defn load-clusters [dir]
  (->> (load-cluster-points dir)
       (merge-with assoc-points
                   (load-cluster-centroids dir))
       (vals)))

;; Utility

(defn square [x]
  (Math/pow x 2))

(defn sqrt [x]
  (Math/sqrt x))


(defn centroid-distances [cluster]
  (let [centroid (:centroid cluster)]
    (->> (:points cluster)
         (map #(distance measure centroid %)))))

(defn squared-errors [cluster]
  (->> (centroid-distances cluster)
       (map i/sq)))

(defn root-mean-square-error [clusters]
  (->> (mapcat squared-errors clusters)
       (s/mean)
       (i/sqrt)))

;; New definition

(defn scatter [cluster]
  (-> (centroid-distances cluster)
      (s/mean)))

(defn assoc-scatter [cluster]
  (assoc cluster :scatter (scatter cluster)))

(defn separation [a b]
  (distance measure (:centroid a) (:centroid b)))

(defn davies-bouldin-ratio [a b]
  (/ (+ (:scatter a)
        (:scatter b))
     (separation a b)))

(defn max-davies-bouldin-ratio [[cluster & clusters]]
  (->> (map #(davies-bouldin-ratio cluster %) clusters)
       (apply max)))

(defn rotations [xs]
  (take (count xs)
        (partition (count xs) 1 (cycle xs))))

(defn davies-bouldin-index [clusters]
  (let [ds (->> (map assoc-scatter clusters)
                (rotations)
                (map max-davies-bouldin-ratio))]
    (s/mean ds)))

;; New Definition

(defn cluster-size [cluster]
  (-> cluster
      centroid-distances
      s/median))

(defn dunn-index [clusters]
  (let [min-separation (->> (combinations clusters 2)
                            (map #(apply separation %))
                            (apply min))
        max-cluster-size (->> (map cluster-size clusters)
                              (apply max))]
    (/ min-separation max-cluster-size)))

;; end

;; Calculate Silhouette - very time complex

(defn point-avg-distance [x xs]
  (->> xs
       (map #(distance measure x %))
       (avg)))

(defn point-silhouette [x cluster-points other-clusters]
  (let [a (point-avg-distance x cluster-points)
        b (->> (map #(point-avg-distance x %) other-clusters)
               (apply min))]
    (/ (- b a)
       (max a b))))

(defn cluster-silhouette [cluster other-clusters]
  (->> (rotations cluster)
       (map #(point-silhouette (first %) (rest %) other-clusters))))

(defn silhouette [clusters]
  (->> (map :points clusters)
       (rotations)
       (mapcat #(cluster-silhouette (first %) (rest %)))
       (avg)))

(defn chart [f label]
  (let [path-prefix "data"
        path-suffixes ["/reuters-sparsevectors-1gram/kmeans-9"
                       "/reuters-sparsevectors-1gram-l2/kmeans-9"
                       "/reuters-sparsevectors-1gram-lnorm/kmeans-9"
                       "/reuters-sparsevectors-1gram-lnorm-l2/kmeans-9"
                       "/reuters-sparsevectors-2gram/kmeans-9"
                       #_"/reuters-sparsevectors-2gram-l2/kmeans-9"
                       "/reuters-sparsevectors-2gram-lnorm/kmeans-9"
                       "/reuters-sparsevectors-2gram-lnorm-l2/kmeans-9"
                       "/reuters-sparsevectors-3gram/kmeans-9"
                       "/reuters-sparsevectors-3gram-l2/kmeans-9"
                       "/reuters-sparsevectors-3gram-lnorm/kmeans-9"
                       "/reuters-sparsevectors-3gram-lnorm-l2/kmeans-9"
                       "/reuters-sparsevectors-4gram/kmeans-9"
                       "/reuters-sparsevectors-4gram-l2/kmeans-9"
                       "/reuters-sparsevectors-4gram-lnorm/kmeans-9"
                       "/reuters-sparsevectors-4gram-lnorm-l2/kmeans-9"]
        paths (map #(str path-prefix %) path-suffixes)
        ys (map (comp f load-clusters) paths)]
    (i/view (c/scatter-plot (range (count paths)) ys :x-label "Clusters" :y-label label))))

;; Streaming Kmeans

(defn streaming-kmeans [n]
  (let [path (str "/Users/henry/Code/cljds.ch6/data/reuters-streamingkmeans/kmeans-" n "/part-r-00000")]
    (->> path
        (seqf/dseq)
        (into [])
        count)))

;; Inter-cluster density

(defn inter-cluster-density [clusters]
  (->> (combinations clusters 2)
       (map #(apply separation %))
       (remove zero?)
       (avg)))

(defn cluster-density [cluster]
  (let [distances (->> (combinations (take 100 (:points cluster)) 2)
                       (map #(apply distance measure %)))
        min (apply min distances)
        max (apply max distances)
        sum (apply + distances)
        count (count distances)]
    (/ (- (/ sum count) min)
       (- max min))))

(defn intra-cluster-density [clusters]
  (->> clusters
       (map cluster-density)
       (avg)))

;; Intra-cluster density
;; double density = (sum / count - min) / (max - min)

(defn points-for [path]
  (->> (seqf/dseq path)
       (into [])
       (map last)
       (take 5)))

(defn plot-perplexity []
  (let [paths ["data/reuters-cvb/cvb-2/model/perplexity*"
               "data/reuters-cvb/cvb-10/model/perplexity*"
               "data/reuters-cvb/cvb-20/model/perplexity*"]]
    (doto (c/line-chart (range 5) (points-for (first paths)) :legend true)
      (c/add-categories (range 5) (points-for (second paths)))
      (c/add-categories (range 5) (points-for (nth paths 2)))
      (i/view))))


(defn cvb-topics []
  ;; Beware mutability!!
  ;; Will always return the last element
  (->> (seqf/dseq "/Users/henry/Code/cljds.ch6/data/reuters-cvb/cvb-2/topics")
       (r/map second)
       (r/map #(.get %))
       (into [])
       (first)))
