(ns finbot.sql
  (:require 
    [finbot.time :as time]
    
    [next.jdbc :as jdbc]
    [hashids.core :as hashids]
    
    [clojure.string :as str]))


(defn jdbc-mysql
  "user@host:port password"
  [config]

  (let [expr (:creds config)
        expr (str/split expr #" ")
        pass (last expr)
        rest (str/split (first expr) #"[@:]")]  
    
    (jdbc/get-datasource
      {:dbtype "mysql"
       :host (nth rest 1)
       :port (nth rest 2)
       :user (nth rest 0)
       :password pass})))


(defn get-category
  "Get agent's category from the last record"
  [ds {:keys [agent chat-id]}]
  (->
    (jdbc/execute! ds
      ["SELECT category FROM telegram.finbot
        WHERE (agent=?
               AND chat_id=?)
        ORDER BY timestamp DESC
        LIMIT 1" 
       agent
       chat-id])
    first
    :finbot/category))


(defn set-category!
  "Populate all agent's records with category"
  [ds {:keys [chat-id
              agent
              category]}]
  (jdbc/execute! ds
    ["UPDATE telegram.finbot
      set category=?
      where (agent=?
             AND chat_id=?)"
     category
     agent
     chat-id]))


(defn insert-row!
  [ds
   config
   {:keys [chat-id  
           message-id
           timestamp
           agent
           amount]
    :as params}]
  
  (let [category
        (get-category ds params)]
  
    (jdbc/execute! ds 
             ["INSERT INTO `telegram`.`finbot`
               (`chat_id`,
                `chat_id_hash`,
                `message_id`,
                `timestamp`,
                `agent`,
                `amount`,
                `active`,
                `category`)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
              chat-id
              (hashids/encode
                {:salt (:creds config)}
                chat-id)
              message-id
              timestamp
              agent
              amount
              true
              (if (nil? category)
                  (do 
                    (set-category! ds (assoc params :category "другое"))
                    "другое")
                  category)])))


(defn get-row
  [ds {:keys [chat-id message-id]}]
  (jdbc/execute-one! ds
    ["SELECT * FROM `telegram`.`finbot`
      WHERE (`chat_id` = ? AND `message_id` = ?)
      ORDER BY `timestamp` ASC LIMIT 1" chat-id message-id]))


(defn delete-row!
  [ds 
   {:keys
    [chat-id 
     message-id]}]
  (jdbc/execute! ds
    ["UPDATE `telegram`.`finbot` 
      SET `active` = ? 
      WHERE (`message_id` = ? AND `chat_id` = ? )" 
     false
     message-id 
     chat-id]))


;;
;; gross
;;



(defn gross-of
  [ds {:keys [chat-id timestamp sign] :or {sign "<"}} fn]
  (->
    (jdbc/execute! ds
      [(format
          "SELECT SUM(`amount`) FROM `telegram`.`finbot`
          WHERE (`chat_id`) = ?
          AND (`timestamp`) > ?
          AND (`amount`) %s 0
          AND (`active`) = 1
          " sign)
       chat-id
       fn])
    first first second))


(defn gross-of-month
  [ds {:keys [chat-id timestamp] :as params}]
  (gross-of ds params (time/start-of-month timestamp)))


(defn gross-of-year
  [ds {:keys [chat-id timestamp] :as params}]
  (gross-of ds params (time/start-of-year timestamp)))


;;
;; agent
;;


(defn gross-of-by-agent
  [ds {:keys [chat-id agent timestamp sign] :or {sign "<"}} fn]
  (->
    (jdbc/execute! ds
      [(format
       "SELECT SUM(`amount`) FROM `telegram`.`finbot`
        WHERE (`chat_id`) = ?
        AND (`timestamp`) > ?
        AND (`agent`) = ?
        AND (`amount`) %s 0
        AND (`active`) = 1" sign)
       chat-id
       fn
       agent])
    first first second))


(defn gross-of-month-by-agent
  [ds {:keys [chat-id agent timestamp] :as params}]
  (gross-of-by-agent ds params (time/start-of-month timestamp)))


(defn gross-of-year-by-agent
  [ds {:keys [chat-id agent timestamp] :as params}]
  (gross-of-by-agent ds params (time/start-of-year timestamp)))


;;
;; category
;;


(defn gross-of-by-category
  [ds {:keys [chat-id category timestamp sign] :or {sign "<"}} fn]
  (->>
    (jdbc/execute! ds
      [(format
       "SELECT SUM(`amount`) FROM `telegram`.`finbot`
        WHERE (`chat_id`) = ?
        AND (`timestamp`) > ?
        AND (`category`) = ?
        AND (`amount`) %s 0
        AND (`active`) = 1" sign)
       chat-id
       fn
       category])
    first first second))


(defn gross-of-month-by-category
  [ds {:keys [chat-id category timestamp] :as params}]
   (gross-of-by-category ds params (time/start-of-month timestamp)))


(defn gross-of-year-by-category
  [ds {:keys [chat-id category timestamp] :as params}]
  (gross-of-by-category ds params (time/start-of-year timestamp)))


;;
;; agent + range
;;



(defn gross-of-by-range
  [ds {:keys [chat-id agent min-amount max-amount timestamp]} fn]
  (some->
    (jdbc/execute! ds
      ["SELECT COUNT(*) FROM `telegram`.`finbot`
        WHERE (`chat_id`) = ?
        AND (`agent`) = ?
        AND (`amount`) >= ?
        AND (`amount`) <= ?
        AND (`timestamp`) > ?
        AND (`active`) = 1"
       chat-id
       agent
       min-amount
       max-amount
       fn])
    first 
    first 
    second
    int)


  )


(defn gross-of-month-by-range
  [ds {:keys [chat-id agent min-amount max-amount timestamp] :as params}]
   (gross-of-by-range ds params (time/start-of-month timestamp)))


(defn gross-of-year-by-range
  [ds {:keys [chat-id agent min-amount max-amount timestamp] :as params}]
   (gross-of-by-range ds params (time/start-of-year timestamp)))


;;
;;
;;


(defn top-4
  [ds {:keys [chat-id]}]
  (jdbc/execute! ds
    ["SELECT `agent`, `amount`, COUNT(*) AS `times` 
      FROM `telegram`.`finbot`
      WHERE `chat_id` = ? 
        AND `timestamp` > ?
        AND `active` = 1
        AND `amount` <= 0
      GROUP BY `agent`, `amount`
      ORDER BY `times` DESC 
      LIMIT 4;"
     chat-id
     (- (System/currentTimeMillis) 2592000000)]))


(defn deactivate-duplicates
  [ds]
  (jdbc/execute! ds
    ["update `telegram`.`finbot`
        set active = ?
        where timestamp in
        (
          select timestamp from
          (
            select timestamp, RN from
            (
                SELECT timestamp, ROW_NUMBER() 
                OVER (PARTITION BY chat_id, message_id 
                  ORDER BY chat_id, message_id) 
                AS RN 
                FROM `telegram`.`finbot`
                as t1
            ) as t2 where RN > 1
          ) as t3
        )
      " 0]))



(comment 
  
  (jdbc/execute! FDS
      [(format
         "SELECT SUM(`amount`) FROM `telegram`.`finbot`
          WHERE (`chat_id`) = ?
          AND (`timestamp`) > ?
          AND (`agent`) = ?
          AND (`amount`) %s 0
          AND (`active`) = 1" "<")
       163440129
       0
       "пятерочка"])
  
  
  (get-category FDS {:agent "пятерочка"
                     :chat-id 163440129})
  
  (deactivate-duplicates FDS)
  
  
  (def CONFIG {:creds (slurp "creds")
               :token (slurp "token")})
  
  (def 
    FDS
    (jdbc-mysql CONFIG))

  (-
    (gross-of-month-by-category FDS
    {:chat-id 914628712
     :timestamp (time/start-of-month (System/currentTimeMillis))
     :category nil}))


  (get-category FDS 
    {:agent "1249213"
     :chat-id 914628712})

  
  (->>
    (top-4 FDS {:chat-id 5362409023})
    (map (fn [x] {:text (format "%s %s"
                          (- (:finbot/amount x))
                          (:finbot/agent x))}))
    (partition 2)
    (map vec)
    vec)
  
  (into [[1 2][3 4]][[5]])

  (gross-of-year-by-agent
    FDS
    {:sign ">"
     :chat-id 163440129
     :agent ""
     :timestamp (System/currentTimeMillis)}
    )
  
  
  (do
   (gross-of-year-by-range
    FDS
    {:chat-id 163440129
     :agent "автобус"
     :min-amount 0
     :max-amount 0
     :timestamp (System/currentTimeMillis)}))
  
  (gross-of-year FDS
    {:chat-id 163440129
     :timestamp (System/currentTimeMillis)})
  (gross-of-year-by-agent FDS
    {:chat-id 163440129
     :agent "пятерочка"
     :timestamp (System/currentTimeMillis)})
  
  (insert-row! FDS 
    {:chat-id 3
     :message-id 3
     :timestamp (System/currentTimeMillis)
     :recipient "кафема"
     :amount 150
     })

  (get-row FDS
    {:chat-id 2
     :message-id 2})
  
  (delete-row! FDS
    {:chat-id 3
     :message-id 3})
  
  #_(get-first-message ds 1)
  
  (:stealer/id #:stealer{:id 6, :bot_id 1, :chat_id 1, :from_chat_id 1, :message_id 1})
  ())



