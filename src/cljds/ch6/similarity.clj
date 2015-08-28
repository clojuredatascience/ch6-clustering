(ns cljds.ch6.similarity
  (:require [clojure.set :as set]
            [incanter.stats :as s]
            [incanter.core :as i]
            [incanter.charts :as c]))

(defn jaccard-similarity [a b]
  (let [a (set a)
        b (set b)]
    (/ (count (set/intersection a b))
       (count (set/union a b)))))

(defn euclidean-distance [a b]
  (->> (map (comp i/sq -) a b)
       (apply +)
       (i/sqrt)))

(defn cosine-similarity [a b]
  (let [dot-product (->> (map * a b)
                         (apply +))
        magnitude (fn [d]
                    (->> (map i/sq d)
                         (apply +)
                         (i/sqrt)))]
    (/ dot-product (* (magnitude a) (magnitude b)))))


(defn dataset-with-outlier []
  (i/bind-rows
   (i/bind-columns
    (s/sample-mvn 200
                  :sigma (i/matrix [[1 0.9]
                                    [0.9 1]])))
   [-2 2.5]))

(defn dataset-of-dimension [d]
  (let [sample-fn #(s/sample-normal d)]
    (i/bind-columns (repeatedly 100 sample-fn))))


(comment 
  (def )


  (def dists (map first (s/mahalanobis-distance data)))
  (i/view (c/bar-chart (range 102) dists))

  (def dists (map first (s/mahalanobis-distance data :W (i/matrix [[1 0] [0 1]]))))
  (i/view (c/bar-chart (range 102) dists))

  (s/mahalanobis-distance [-1.75 1.75] :y data)
  (s/mahalanobis-distance [-1.75 1.75]
                          :y data
                          :W (i/matrix [[1 0]
                                        [0 1]]))
  )
