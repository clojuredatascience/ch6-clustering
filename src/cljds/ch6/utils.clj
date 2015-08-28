(ns cljds.ch6.utils
  (:import [org.apache.log4j Logger Level WriterAppender SimpleLayout]))

(defn bootstrap []
  (.setLevel (Logger/getRootLogger) Level/ERROR)
  (-> (Logger/getRootLogger)
      (.addAppender (WriterAppender. (SimpleLayout.) *out*))))

(comment (bootstrap))
