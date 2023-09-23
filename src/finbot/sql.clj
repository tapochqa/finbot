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


(defn insert-row!
  [ds
   config
   {:keys [chat-id  
              message-id
              timestamp
              agent
              amount]}]

  (jdbc/execute! ds 
           ["INSERT INTO `telegram`.`finbot`
             (`chat_id`,
              `chat_id_hash`,
              `message_id`,
              `timestamp`,
              `agent`,
              `amount`,
              `active`)
             VALUES (?, ?, ?, ?, ?, ?, ?)"
            chat-id
            (hashids/encode
              {:salt (:creds config)}
              chat-id)
            message-id
            timestamp
            agent
            amount
            true]))


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


(defn gross-of-month
  [ds {:keys [chat-id timestamp]}]
  (->
    (jdbc/execute! ds
      ["SELECT SUM(`amount`) FROM `telegram`.`finbot`
        WHERE (`chat_id`) = ?
        AND (`timestamp`) > ?
        AND (`amount`) < 0
        AND (`active`) = 1
        "
       chat-id
       (time/start-of-month timestamp)])
    first first second))


(defn gross-of-month-by-agent
  [ds {:keys [chat-id agent timestamp]}]
  (->
    (jdbc/execute! ds
      ["SELECT SUM(`amount`) FROM `telegram`.`finbot`
        WHERE (`chat_id`) = ?
        AND (`timestamp`) > ?
        AND (`agent`) = ?
        AND (`amount`) < 0
        AND (`active`) = 1"
       chat-id
       (time/start-of-month timestamp)
       agent])
    first first second))


(comment 
  
  
  
  
  
  (def CONFIG {:creds (slurp "creds")
               :token (slurp "token")})
  (def 
    FDS
    (jdbc-mysql CONFIG))
  
  (gross-of-month FDS 
    {:chat-id 163440129})
  (gross-of-month-by-agent FDS 
    {:chat-id 163440129
     :agent "Пятерочка"}
    )
  
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



