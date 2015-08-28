(ns cljds.ch6.kmeans
  (:require [incanter.core :as i]
            [incanter.stats :as s]
            [incanter.charts :as c]
            [cljds.ch6.tokens :refer :all]))

(defn indices-of [coll value]
  (keep-indexed (fn [idx x]
                  (when (= x value)
                    idx)) coll))

(defn index-of [coll value]
  (first (indices-of coll value)))

(defn conj-into [m coll]
  (let [f (fn [m [k v]]
            (update-in m [k] conj v))]
    (reduce f m coll)))

(defn centroid [xs]
  (let [m (i/trans (i/matrix xs))]
    (if (> (i/ncol m) 1)
      (i/matrix (map s/mean m))
      m)))

(defn clusters [cluster-ids data]
  (->> (map vector cluster-ids data)
       (conj-into {})
       (vals)
       (map i/matrix)))

(defn k-means [data k]
  (loop [centroids (s/sample data :size k)
         previous-cluster-ids nil]
    (let [cluster-id (fn [x]
                       (let [similarity  #(s/cosine-similarity x %)
                             similarities (map similarity centroids)]
                         (->> (apply max similarities)
                              (index-of similarities))))
          cluster-ids (map cluster-id data)
          clustered (clusters cluster-ids data)]
      (if (not= cluster-ids previous-cluster-ids)
        (recur (map centroid clustered)
               cluster-ids)
        clustered))))

(defn cluster-summary [clusters dict top-term-count]
  (for [cluster clusters]
    (let [sum-terms (if (= (i/nrow cluster) 1)
                      cluster
                      (->> (i/trans cluster)
                           (map s/mean)
                           (i/trans)))
          popular-term-ids (->> (map-indexed vector sum-terms)
                                (sort-by second >)
                                (take top-term-count)
                                (map first))
          top-terms (map #(id->term dict %) popular-term-ids)]
      (println "N:" (i/nrow cluster))
      (println "Terms:" top-terms))))
