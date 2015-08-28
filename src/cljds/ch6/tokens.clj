(ns cljds.ch6.tokens
  (:require [clojure.core.reducers :as r]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [incanter.core :as i]
            [opennlp.nlp :refer [make-tokenizer]]
            [stemmers.core :as stemmer]))
 
(defn tokenize [s]
  (str/split s #"\W+"))

(defn n-grams [n words]
  (->> (partition n 1 words)
       (map (partial str/join " "))))

(defn multi-grams [n words]
  (->> (range 1 (inc n))
       (mapcat #(n-grams % words))))

(def dictionary
  (atom {:count 0
         :terms {}}))

(defn add-term-to-dict [dict word]
  (if (contains? (:terms dict) word)
    dict
    (-> dict
        (update-in [:terms] assoc word (get dict :count))
        (update-in [:count] inc))))

(defn add-term-to-dict! [dict term]
  (doto dict
    (swap! add-term-to-dict term)))

(defn build-dictionary! [dict terms]
  (reduce add-term-to-dict! dict terms))

(defn term-id [dict term]
  (get-in @dict [:terms term]))

(defn id->term [dict term-id]
  (some (fn [[term id]]
          (when (= id term-id)
            term))
        (:terms @dict)))

(defn term-frequencies [dict terms]
  (->> (map #(term-id dict %) terms)
       (remove nil?)
       (frequencies)))

(defn map->vector [dictionary id-counts]
  (let [zeros (vec (replicate (:count @dictionary) 0))]
    (-> (reduce #(apply assoc! %1 %2) (transient zeros) id-counts)
        (persistent!))))

(defn tf-vector [dict document]
  (map->vector dict (term-frequencies dict document)))

(defn inc-df! [dictionary term-id]
  (doto dictionary
    (swap! update-in [:df term-id] (fnil inc 0))))

(defn build-df-dictionary! [dictionary document]
  (let [terms    (distinct document)
        dict     (build-dictionary! dictionary document)
        term-ids (map #(term-id dictionary %) document)]
    (doseq [term-id term-ids]
      (inc-df! dictionary term-id))
    dict))

(defn document-frequencies [dict terms]
  (->> (map (partial term-id dict) terms)
       (select-keys (:df @dict))))

(defn tfidf-vector [dict doc-count terms]
  (let [tf (term-frequencies dict terms)
        df (document-frequencies dict (distinct terms))
        idf   (fn [df] (i/log (/ doc-count df)))
        tfidf (fn [tf df] (* tf (idf df)))]
    (map->vector dict (merge-with tfidf tf df))))

(defn add-documents-to-dictionary! [dict documents]
  (reduce build-dictionary! dict documents))

(def stopwords
  #{"a" "about" "above" "across" "after" "afterwards" "again" "against" "all" "almost" "alone" "along" "already" "also" "although" "always" "am" "among" "amongst" "amoungst" "amount" "an" "and" "another" "any" "anyhow" "anyone" "anything" "anyway" "anywhere" "are" "around" "as" "at" "back" "be" "became" "because" "become" "becomes" "becoming" "been" "before" "beforehand" "behind" "being" "below" "beside" "besides" "between" "beyond" "bill" "both" "bottom" "but" "by" "call" "can" "cannot" "cant" "co" "computer" "con" "could" "couldnt" "cry" "de" "describe" "detail" "do" "done" "down" "due" "during" "each" "eg" "eight" "either" "eleven" "else" "elsewhere" "empty" "enough" "etc" "even" "ever" "every" "everyone" "everything" "everywhere" "except" "few" "fifteen" "fify" "fill" "find" "fire" "first" "five" "for" "former" "formerly" "forty" "found" "four" "from" "front" "full" "further" "get" "give" "go" "had" "has" "hasnt" "have" "he" "hence" "her" "here" "hereafter" "hereby" "herein" "hereupon" "hers" "herself" "him" "himself" "his" "how" "however" "hundred" "i" "ie" "if" "in" "inc" "indeed" "interest" "into" "is" "it" "its" "itself" "keep" "last" "latter" "latterly" "least" "less" "ltd" "made" "many" "may" "me" "meanwhile" "might" "mill" "mine" "more" "moreover" "most" "mostly" "move" "much" "must" "my" "myself" "name" "namely" "neither" "never" "nevertheless" "next" "nine" "no" "nobody" "none" "noone" "nor" "not" "nothing" "now" "nowhere" "of" "off" "often" "on" "once" "one" "only" "onto" "or" "other" "others" "otherwise" "our" "ours" "ourselves" "out" "over" "own" "part" "per" "perhaps" "please" "put" "rather" "re" "same" "see" "seem" "seemed" "seeming" "seems" "serious" "several" "she" "should" "show" "side" "since" "sincere" "six" "sixty" "so" "some" "somehow" "someone" "something" "sometime" "sometimes" "somewhere" "still" "such" "system" "take" "ten" "than" "that" "the" "their" "them" "themselves" "then" "thence" "there" "thereafter" "thereby" "therefore" "therein" "thereupon" "these" "they" "thick" "thin" "third" "this" "those" "though" "three" "through" "throughout" "thru" "thus" "to" "together" "too" "top" "toward" "towards" "twelve" "twenty" "two" "un" "under" "until" "up" "upon" "us" "very" "via" "was" "we" "well" "were" "what" "whatever" "when" "whence" "whenever" "where" "whereafter" "whereas" "whereby" "wherein" "whereupon" "wherever" "whether" "which" "while" "whither" "who" "whoever" "whole" "whom" "whose" "why" "will" "with" "within" "without" "would" "yet" "you" "your" "yours" "yourself" "yourselves"})

(defn too-short? [content]
  (< (count (str content)) 500))

(defn tokenize-reuters [content]
  (-> (str/replace content  #"^.*\n\n" "")
      (str/lower-case)
      (tokenize)))

(defn stem-reuters [content]
  (-> (str/replace content  #"^.*\n\n" "")
      (stemmer/stems)))

(defn reuters-terms [file]
  (-> (io/resource file)
      (slurp)
      (tokenize-reuters)))
