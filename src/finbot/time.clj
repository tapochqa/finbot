(ns finbot.time
  (:require 
    [java-time.api :as java-time]))


(defn start-of
  [unix kw]
  (-> unix
    java-time/instant
    (java-time/local-date-time "UTC")
    (java-time/adjust kw)
    (java-time/truncate-to :days)
     java-time/local-date
    (str "T00:00:00Z")
     java-time/instant
     java-time/to-millis-from-epoch))


(defn start-of-month
  [unix]
  (start-of unix :first-day-of-month))

(defn start-of-year
  [unix]
  (start-of unix :first-day-of-year))


(comment
  
  (start-of-month (System/currentTimeMillis))
  (start-of-year (System/currentTimeMillis)))