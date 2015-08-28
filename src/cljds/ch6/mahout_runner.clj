(ns cljds.ch6.mahout-runner
  (:import [org.apache.mahout.text SequenceFilesFromDirectory]
           [org.apache.lucene.benchmark.utils ExtractReuters]
           [org.apache.mahout.vectorizer SparseVectorsFromSequenceFiles]
           [org.apache.mahout.clustering.kmeans KMeansDriver]
           [org.apache.mahout.common.distance CosineDistanceMeasure]
           [org.apache.mahout.utils.clustering ClusterDumper]
           [org.apache.mahout.clustering.spectral.kmeans SpectralKMeansDriver]
           [org.apache.mahout.clustering.lda.cvb CVB0Driver]))

(defn run-kmeans [in-path clusters-path out-path k]
  (let [distance-measure  "org.apache.mahout.common.distance.CosineDistanceMeasure"
        max-iterations    100
        convergence-delta 0.001]
    (KMeansDriver/main
     (->> (vector "-i"  in-path
                  "-c"  clusters-path
                  "-o"  out-path
                  "-dm" distance-measure
                  "-x"  max-iterations
                  "-k"  k
                  "-cd" convergence-delta
                  "-ow"
                  "-cl")
          (map str)
          (into-array String)))))

(defn run-cluster-dump [in-path dict-path points-dir out-path]
  (let [distance-measure
        "org.apache.mahout.common.distance.CosineDistanceMeasure"]
    (ClusterDumper/main
     (->> (vector "-i" in-path
                  "-o" out-path
                  "-d" dict-path
                  "--pointsDir" points-dir
                  "-dm" distance-measure
                  "-dt" "sequencefile"
                  "-b" "100"
                  "-n" "20"
                  "-sp" "0"
                  "--evaluate")
          (map str)
          (into-array String)))))

;; 

(defn run-spectral-clustering [in-path out-path k]
  (let [distance-measure "org.apache.mahout.common.distance.CosineDistanceMeasure"
        dimensions     21578
        max-iterations 100
        convergence-delta 0.001]
    (SpectralKMeansDriver/main
     (->> (vector "-i"  in-path
                  "-o"  out-path
                  "-dm" distance-measure
                  "-d" dimensions
                  "-k" k
                  "-x" max-iterations
                  "-cd" convergence-delta
                  "-ow")
          (map str)
          (into-array String)))))

(defn run-lda [in-path out-path]
  (let [max-iterations 35
        convergence-delta 0.001
        num-clusters 10
        dictionary "data/parkour-vectors/dictionary/part-r-00000"
        docs-topics-path "data/reuters-topics"
        model-tempdir "data/reuters-topics-temp"]
    (CVB0Driver/main
     (->> (vector "-i" in-path
                  "-o" out-path
                  "--maxIter" max-iterations
                  "-cd" convergence-delta
                  "-k" num-clusters
                  "-dict" dictionary
                  "-dt" docs-topics-path
                  "-mt" model-tempdir
                  "-block" 1
                  "-ow")
          (map str)
          (into-array String)))))
