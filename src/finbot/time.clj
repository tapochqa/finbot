(ns finbot.time
  (:require 
    [java-time.api :as java-time]))



(defn start-of-month
  [unix]
  (-> unix
    java-time/instant
    (java-time/local-date-time "UTC")
    (java-time/adjust :first-day-of-month)
    (java-time/truncate-to :days)
     java-time/local-date
    (str "T00:00:00Z")
     java-time/instant
     java-time/to-millis-from-epoch))


(comment
  
  (start-of-month (System/currentTimeMillis)))