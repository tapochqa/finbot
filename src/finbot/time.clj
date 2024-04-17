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


(defn which-month
  [unix]
  (case
    (-> unix
      java-time/instant
      (java-time/local-date "UTC")
      str
      (subs 5 7)
      parse-long)

    1 "январе"
    2 "феврале"
    3 "марте"
    4 "апреле"
    5 "мае"
    6 "июне"
    7 "июле"
    8 "августе"
    9 "сентябре"
    10 "октябре"
    11 "ноябре"
    12 "декабре"))


(defn which-year
  [unix]
  (-> unix
      java-time/instant
      (java-time/local-date "UTC")
      str
      (subs 0 4)))



(comment
  (which-year (System/currentTimeMillis))
  (which-month (System/currentTimeMillis))
  (start-of-month (System/currentTimeMillis))
  (start-of-year (System/currentTimeMillis)))