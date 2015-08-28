(ns cljds.ch6.vectorizer
  (:require [abracad.avro :as avro]
            [cljds.ch6.tokens :refer [multi-grams]]
            [cljds.ch6.utils :refer [bootstrap]]
            [clojure.core.reducers :as r]
            [parkour (conf :as conf) (fs :as fs)
             ,       (toolbox :as ptb) (tool :as tool)]
            [parkour.graph :as pg]
            [parkour.io (dseq :as dseq) (text :as text) (avro :as mra)
             ,          (seqf :as seqf)
             ,          (dsink :as dsink)]
            [parkour.io.dux :as dux]
            [parkour.io.dval :as dval]
            [parkour.mapreduce :as mr]
            [parkour.wrapper :refer [Wrapper]]
            [stemmers.core :as stemmer]
            [transduce.reducers :as tr])
  (:import [org.apache.hadoop.io Text LongWritable IntWritable]
           [org.apache.mahout.clustering.classify WeightedVectorWritable WeightedPropertyVectorWritable]
           [org.apache.mahout.clustering.iterator ClusterWritable]
           [org.apache.mahout.math VectorWritable RandomAccessSparseVector NamedVector]))


;; Utils

(extend-protocol Wrapper

  VectorWritable
  (unwrap [wobj] (.get wobj))
  (rewrap [wobj obj]
    (doto wobj
      (.set ^Vector obj)))
  
  ClusterWritable
  (unwrap [wobj] (.getValue wobj))
  (rewrap [wobj obj]
    (doto wobj
      (.setValue ^Cluster obj)))
  
  WeightedVectorWritable
  (unwrap [wobj] (.getVector wobj))
  (rewrap [wobj obj]
    (doto wobj
      (.setVector ^Vector obj))))

(defn global-id [offsets [global-offset local-offset]]
  (+ local-offset (get offsets global-offset)))

(defn calculate-offsets [dseq]
  (->> (into [] dseq)
       (sort-by first)
       (reductions (fn [[_ t] [i n]]
                     [(inc i) (+ t n)])
                   [0 0])
       (into {})))

(defn parse-idf [[word id idf]]
  [word {:id id
         :idf idf}])

;; Avro schemas

(def long-pair (avro/tuple-schema [:long :long]))
(def index-value (avro/tuple-schema [long-pair :long]))
(def word-id (avro/tuple-schema [:string :long]))

(def words (avro/tuple-schema [:string :long :double]))

;; TF

(defn document->terms [doc]
  (clojure.string/split doc #"\W+"))

(defn term-frequencies [document]
  (->> (stemmer/stems document)
       (multi-grams 4)
       (frequencies)))

(defn too-short? [document]
  (< (count (str document)) 500))

(defn term-frequency-m
  {::mr/source-as :vals
   ::mr/sink-as :keyvals}
  [documents]
  (->> (r/remove too-short? documents)
       (r/mapcat term-frequencies)))

(defn unique-id-r
  {::mr/source-as :keyvalgroups,
   ::mr/sink-as dux/named-keyvals}
  [coll]
  (let [global-offset (conf/get-long mr/*context*
                                     "mapred.task.partition" -1)]
    (tr/mapcat-state
     (fn [local-offset [word counts]]
       [(inc local-offset)
        (if (identical? ::finished word)
          [[:counts [global-offset local-offset]]]
          [[:data [word [global-offset local-offset]]]])])
     0 (r/mapcat identity [coll [[::finished nil]]]))))

(defn unique-id-j [dseq]
  (-> (pg/input dseq)
      (pg/map #'term-frequency-m)
      (pg/partition (mra/shuffle [:string :long]))
      (pg/reduce #'unique-id-r)
      (pg/output :data   (mra/dsink [:string long-pair])
                 :counts (mra/dsink [:long :long]))))

(defn word-id-m
  {::mr/sink-as :keys}
  [offsets-dval coll]
  (let [offsets @offsets-dval]
    (r/map
     (fn [[word word-offset]]
       [word (global-id offsets word-offset)])
     coll)))

(defn unique-word-ids [conf df-data df-counts]
  (let [offsets-dval (-> (calculate-offsets df-counts)
                         (dval/edn-dval))]
    (-> (pg/input df-data)
        (pg/map #'word-id-m offsets-dval)
        (pg/output (mra/dsink [word-id]))
        (pg/fexecute conf `word-id)
        (->> (r/map parse-idf)
             (into {}))
        (dval/edn-dval))))

(defn create-sparse-vector [dictionary [id document]]
  (let [vector (RandomAccessSparseVector. (count dictionary))]
    (doseq [[term tf] (term-frequencies document)]
      (if-let [term-info (get dictionary term)]
        (.setQuick vector (:id term-info) tf)))
    [id vector]))

(defn create-vectors-m
  {::mr/source-as :keyvals ::mr/sink-as :keyvals}
  [dictionary documents]
  (let [dictionary @dictionary]
    (->> (r/remove too-short? documents)
         (r/map (partial create-sparse-vector dictionary)))))

;; Shared
(defn write-dictionary [path dictionary-dval]
  (dsink/with-dseq
    (seqf/dsink [Text IntWritable] path)
    (map (fn [[word {:keys [id]}]]
           (vector word id)) @dictionary-dval)))

(defn tf* [conf dseq dictionary-path vector-path]
  (let [[df-data df-counts] (pg/execute (unique-id-j dseq) conf `uid)
        dictionary-dval (unique-word-ids conf df-data df-counts)]
    (write-dictionary dictionary-path dictionary-dval)
    (-> (pg/input dseq)
        (pg/map #'create-vectors-m dictionary-dval)
        (pg/output (seqf/dsink [Text VectorWritable] vector-path))
        (pg/fexecute conf `vectorize))))


(defn tf [conf input output]
  (let [dseq (seqf/dseq input)
        dictionary-path (doto (str output "/dictionary") fs/path-delete)
        vector-path (doto (str output "/vectors") fs/path-delete)]
    (tf* conf dseq dictionary-path vector-path)))

(defn dictionary-job [conf input-path output-path]
  (let [dseq (seqf/dseq input-path)
        dictionary-path (doto (str output-path "/dictionary") fs/path-delete)
        [df-data df-counts] (pg/execute (unique-id-j dseq) conf `uid)
        dictionary-dval (unique-word-ids conf df-data df-counts)]
    (write-dictionary dictionary-path dictionary-dval)))

;; TF-IDF

(defn document-count-m
  {::mr/source-as :vals}
  [documents]
  (->> documents
       (r/mapcat (comp distinct stemmer/stems))
       (r/map #(vector % 1))))

(defn unique-index-r
  {::mr/source-as :keyvalgroups,
   ::mr/sink-as dux/named-keyvals}
  [coll]
  (let [global-offset (conf/get-long mr/*context*
                                     "mapred.task.partition" -1)]
    (tr/mapcat-state
     (fn [local-offset [word doc-counts]]
       [(inc local-offset)
        (if (identical? ::finished word)
          [[:counts [global-offset local-offset]]]
          [[:data [word [[global-offset local-offset]
                         (apply + doc-counts)]]]])])
     0 (r/mapcat identity [coll [[::finished nil]]]))))

(defn words-idf-m
  {::mr/sink-as :keys}
  [offsets-dval n coll]
  (let [offsets @offsets-dval]
    (r/map
     (fn [[word [word-offset df]]]
       [word (global-id offsets word-offset) (Math/log (/ n df))])
     coll)))

(defn df-j [dseq]
  (-> (pg/input dseq)
      (pg/map #'document-count-m)
      (pg/partition (mra/shuffle [:string :long]))
      (pg/reduce #'unique-index-r)
      (pg/output :data (mra/dsink [:string index-value])
                 :counts (mra/dsink [:long :long]))))

(defn make-dictionary [conf df-data df-counts doc-count]
  (let [offsets-dval (dval/edn-dval (calculate-offsets df-counts))]
    (-> (pg/input df-data)
        (pg/map #'words-idf-m offsets-dval doc-count)
        (pg/output (mra/dsink [words]))
        (pg/fexecute conf `idf)
        (->> (r/map parse-idf)
             (into {}))
        (dval/edn-dval))))

(defn create-sparse-tfidf-vector [dictionary [id doc]]
  (let [vector (RandomAccessSparseVector. (count dictionary))]
    (doseq [[term tf] (-> doc stemmer/stems frequencies)]
      (let [term-info (get dictionary term)
            id  (:id term-info)
            idf (:idf term-info)]
        (.setQuick vector id (* tf idf))))
    [id vector]))

(defn create-tfidf-vectors-m [dictionary coll]
  (let [dictionary @dictionary]
    (r/map #(create-sparse-tfidf-vector dictionary %) coll)))

(defn tfidf [conf dseq dictionary-path vector-path]
  (let [doc-count (->> dseq (into []) count)
        [df-data df-counts] (pg/execute (df-j dseq) conf `df)
        dictionary-dval (make-dictionary conf df-data
                                         df-counts doc-count)]
    (write-dictionary dictionary-path dictionary-dval)
    (-> (pg/input dseq)
        (pg/map #'create-tfidf-vectors-m dictionary-dval)
        (pg/output (seqf/dsink [Text VectorWritable] vector-path))
        (pg/fexecute conf `vectorize))))

(defn tfidf-job [conf input output]
  (let [dseq (seqf/dseq input)
        dictionary-path (doto (str output "/dictionary") fs/path-delete)
        vector-path (doto (str output "/vectors") fs/path-delete)]
    (tfidf conf dseq dictionary-path vector-path)))
