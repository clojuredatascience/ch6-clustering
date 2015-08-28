(ns cljds.ch6.data
  (:require [parkour.conf :as conf]
            [parkour.io.text :as text]
            [parkour.io.seqf :as seqf]
            [clojure.core.reducers :as r]
            [me.raynes.fs :as fs])
  (:import [org.apache.mahout.text SequenceFilesFromDirectory]
           [org.apache.lucene.benchmark.utils ExtractReuters]
           [org.apache.mahout.vectorizer SparseVectorsFromSequenceFiles]
           [org.apache.mahout.clustering.kmeans KMeansDriver]
           [org.apache.mahout.common.distance CosineDistanceMeasure]
           [org.apache.mahout.utils.clustering ClusterDumper]
           [org.apache.mahout.clustering.spectral.kmeans SpectralKMeansDriver]
           [org.apache.mahout.clustering.lda.cvb CVB0Driver]))

(defn sgml->text [in-path out-path]
  (let [in-file  (clojure.java.io/file in-path)
        out-file (clojure.java.io/file out-path)]
    (.extract (ExtractReuters. in-file out-file))))

(defn text->sequencefile [in-path out-path]
  (SequenceFilesFromDirectory/main
   (into-array String (vector "-i" in-path
                              "-o" out-path
                              "-xm" "sequential"
                              "-ow"))))

(defn extract-reuters [in-path tmp-path out-path]
  (sgml->text in-path tmp-path)
  (text->sequencefile tmp-path out-path))

(defn sequencefile->vectors [in-path out-path]
  (SparseVectorsFromSequenceFiles/main
   (into-array String (vector "-i" in-path "-o" out-path))))
