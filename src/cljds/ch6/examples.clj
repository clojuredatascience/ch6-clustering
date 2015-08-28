(ns cljds.ch6.examples
  (:require [cljds.ch6.data :refer :all]
            [cljds.ch6.evaluation :refer :all]
            [cljds.ch6.kmeans :refer :all]
            [cljds.ch6.mahout-runner :refer :all]
            [cljds.ch6.similarity :refer :all]
            [cljds.ch6.tokens :refer :all]
            [cljds.ch6.vectorizer :as vectorizer]
            [clojure.core.reducers :as r]
            [clojure.java.io :as io]
            [incanter.charts :as c]
            [incanter.core :as i]
            [incanter.stats :as s]
            [incanter.svg :as svg]
            [me.raynes.fs :as fs]
            [parkour.conf :as conf]
            [parkour.io.seqf :as seqf]
            [parkour.io.text :as text]
            [parkour.tool :as tool]
            [stemmers.core :as stemmer]))

(def doc-a
  "reut2-020.sgm-962.txt")

(def doc-b
  "reut2-020.sgm-761.txt")

(def doc-c
  "reut2-020.sgm-932.txt")

(def doc-d
  "reut2-020.sgm-742.txt")

(defn ex-6-0 []
  (when-not (sgml->text "data/reuters-sgml"
                        "data/reuters-text")
    (println "Extracted documents successfully.")))

(defn ex-6-1 []
  (let [a [1 2 3]
        b [2 3 4]]
    (jaccard-similarity a b)))

(defn ex-6-2 []
  (let [a (set (reuters-terms "reut2-020.sgm-761.txt"))
        b (set (reuters-terms "reut2-007.sgm-750.txt"))
        j (jaccard-similarity a b)]
    (println "A:" a)
    (println "B:" b)
    (println "Similarity:" j)))

(defn ex-6-3 []
  (let [doc  (reuters-terms "reut2-020.sgm-742.txt")
        dict (build-dictionary! dictionary doc)]
    (println "Document:" doc)
    (println "Dictionary:" dict)
    (println "Vector:" (tf-vector dict doc))))

(defn print-distance [doc-a doc-b measure]
  (let [a-terms (reuters-terms doc-a)
        b-terms (reuters-terms doc-b)
        dict (-> dictionary
                 (build-dictionary! a-terms)
                 (build-dictionary! b-terms))
        a (tf-vector dict a-terms)
        b (tf-vector dict b-terms)]
    (println "A:" a)
    (println "B:" b)
    (println "Distance:" (measure a b))))

(defn ex-6-4 []
  (print-distance "reut2-020.sgm-742.txt"
                  "reut2-020.sgm-932.txt"
                  euclidean-distance))

(defn ex-6-5 []
  (print-distance "reut2-020.sgm-742.txt"
                  "reut2-020.sgm-932.txt"
                  cosine-similarity))

(defn ex-6-6 []
  (let [a (tokenize "music is the food of love")
        b (tokenize "war is the locomotive of history")]
    (add-documents-to-dictionary! dictionary [a b])
    (cosine-similarity (tf-vector dictionary a)
                       (tf-vector dictionary b))))

(defn ex-6-7 []
  (let [a (tokenize "music is the food of love")
        b (tokenize "it's lovely that you're musical")]
    (add-documents-to-dictionary! dictionary [a b])
    (cosine-similarity (tf-vector dictionary a)
                       (tf-vector dictionary b))))

(defn ex-6-8 []
  (let [a (stemmer/stems "music is the food of love")
        b (stemmer/stems "it's lovely that you're musical")]
    (add-documents-to-dictionary! dictionary [a b])
    (cosine-similarity (tf-vector dictionary a)
                       (tf-vector dictionary b))))

(defn ex-6-9 []
  (let [m (i/matrix [[1 2 3]
                     [2 2 5]])]
    (centroid m)))

(defn ex-6-10 []
  (let [m (i/matrix [[1 2 3]
                     [4 5 6]
                     [7 8 9]])]
    (clusters [0 1 0] m)))

(defn ex-6-11 []
  (let [documents (fs/glob "data/reuters-text/*.txt")
        doc-count 100
        k 5
        tokenized (->> (map slurp documents)
                       (remove too-short?)
                       (take doc-count)
                       (map stem-reuters))]
    (add-documents-to-dictionary! dictionary tokenized)
    (-> (map #(tf-vector dictionary %) tokenized)
        (k-means k))))

(defn ex-6-12 []
  (cluster-summary (ex-6-11) dictionary 5))


(defn ex-6-13 []
  (let [documents (fs/glob "data/reuters-text/*.txt")
        doc-count 1000
        top-terms 25
        term-frequencies (->> (map slurp documents)
                              (remove too-short?)
                              (take doc-count)
                              (mapcat tokenize-reuters)
                              (frequencies)
                              (vals)
                              (sort >)
                              (take top-terms))]
    (-> (c/xy-plot (range (inc top-terms)) term-frequencies
                   :x-label "Terms"
                   :y-label "Term Frequency")
        (i/view))))

(defn ex-6-14 []
  (let [documents (fs/glob "data/reuters-text/*.txt")
        doc-count 100
        k 5
        tokenized (->> (map slurp documents)
                       (remove too-short?)
                       (take doc-count)
                       (map stem-reuters))]
    (reduce build-df-dictionary! dictionary tokenized)
    (-> (map #(tfidf-vector dictionary doc-count %) tokenized)
        (k-means k)
        (cluster-summary dictionary 10))))

(defn ex-6-15 []
  (let [terms (reuters-terms "reut2-020.sgm-761.txt")]
    (n-grams 2 terms)))

(defn ex-6-16 []
  (let [terms (reuters-terms "reut2-020.sgm-761.txt")]
    (multi-grams 4 terms)))


;; Mahout

(defn ex-6-17 []
  (text->sequencefile "data/reuters-text"
                      "data/reuters-sequencefile"))


;; Creating a dictionary


(defn ex-6-18 []
  (let [input-path  "data/reuters-sequencefile" 
        output-path "data/reuters-vectors"]
    (vectorizer/tfidf-job (conf/ig) input-path output-path)))

(defn ex-6-19 []
  (run-kmeans "data/reuters-vectors/vectors"
              "data/kmeans-clusters/clusters"
              "data/kmeans-clusters"
              10))

(defn path-for [path]
  (-> (fs/glob path)
      (first)
      (.getAbsolutePath)))

(defn ex-6-20 []
  (run-cluster-dump
   (path-for "data/kmeans-clusters/clusters-*-final")
   "data/reuters-vectors/dictionary/part-r-00000"
   "data/kmeans-clusters/clusteredPoints"
   "data/kmeans-clusterdump"))

(defn ex-6-21 []
  (doseq [k (range 2 21)
          :let [dir (str "data/kmeans-clusters-" k)]]
    (println dir)
    (run-kmeans "data/reuters-vectors/vectors"
                (str dir "/clusters")
                dir k)))

(defn ex-6-22 []
  (let [ks (range 2 21)
        ys (for [k ks
                 :let [dir (str "data/kmeans-clusters-" k)
                       clusters (load-clusters dir)]]
             (root-mean-square-error clusters))]
    (-> (c/scatter-plot ks ys
                        :x-label "k"
                        :y-label "RMSE")
        (i/view))))


(defn ex-6-23 []
  (let [ks (range 2 21)
        ys (for [k ks
                 :let [dir (str "data/kmeans-clusters-" k)
                       clusters (load-clusters dir)]]
             (dunn-index clusters))]
    (-> (c/scatter-plot ks ys
                        :x-label "k"
                        :y-label "Dunn Index")
        (i/view))))


(defn ex-6-24 []
  (let [ks (range 2 21)
        ys (for [k ks
                 :let [dir (str "data/kmeans-clusters-" k)
                       clusters (load-clusters dir)]]
             (davies-bouldin-index clusters))]
    (-> (c/scatter-plot ks ys
                        :x-label "k"
                        :y-label "Davies-Bouldin Index")
        (i/view))))

(defn visulize-anomaly []
  (let [data (dataset-with-outlier)
        [x y] (i/trans data)]
    (-> (c/scatter-plot x y)
        (c/add-points [(s/mean x)] [(s/mean y)])
        (c/add-pointer -2 2.5)
        (i/view))))

(defn ex-6-25 []
  (let [data (dataset-with-outlier)
        centroid  (i/matrix [[0 0]])
        distances (map #(s/euclidean-distance centroid %) data)]
    (-> (c/bar-chart (range 202) distances
                     :x-label "Points"
                     :y-label "Euclidean Distance") 
        (i/view))))

(defn ex-6-26 []
  (let [data (dataset-with-outlier)
        distances    (map first (s/mahalanobis-distance data))]
    (-> (c/bar-chart (range 202) distances
                     :x-label "Points"
                     :y-label "Mahalanobis Distance")
        (i/view))))

(defn ex-6-27 []
  (let [distances (for [d (range 2 100)
                        :let [data (->> (dataset-of-dimension d)
                                        (s/mahalanobis-distance)
                                        (map first))]]
                    [(apply min data) (apply max data)])]
    (-> (c/xy-plot (range 2 101) (map first distances)
                   :x-label "Number of Dimensions"
                   :y-label "Distance Between Points"
                   :series-label "Minimum Distance"
                   :legend true)
        (c/add-lines (range 2 101) (map second distances)
                     :series-label "Maximum Distance")
        (i/view))))
