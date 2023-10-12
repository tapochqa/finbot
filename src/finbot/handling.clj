(ns finbot.handling
  (:require
    [tg-bot-api.telegram :as telegram]
    [clojure.string :as str]
    [finbot.sql :as sql]
    [hashids.core :as hashids]))


(def REGEXPS
  {:transaction
   #"\+?([-.0-9]+) ([^\n]+)"
   
   :category
   #"(.*) [-—] (.*)"})


(defn inline-keyboard
  [{message-id :message_id}]
  [[{:text "⨯"
     :callback_data message-id}]])


(defn keyboard
  [config ds message]
  (into 
    (->>
      (sql/top-4 ds {:chat-id (-> message :chat :id)})
      (map (fn [x] {:text (format "%s %s"
                            (- (:finbot/amount x))
                            (:finbot/agent x))}))
      (partition 2)
      (map vec)
      vec)
    [[{:text "Дашборд"
       :web_app {:url (format 
                        "https://datalens.yandex/4anikzd90wr2t?chat_id_hash=%s"
                        (hashids/encode
                          {:salt (:creds config)}
                          (-> message :chat :id)))}}]]))


(defn ok
  [config ds message]
  (telegram/send-message
    config
    (-> message :chat :id)
    "ок"
    {:reply-markup
     {:keyboard (keyboard config ds message)}}))


(defn the-handler 
  "Bot logic here"
  [config {:keys [message callback_query]} trigger-id]
  
  (let [ds
        (sql/jdbc-mysql config)]
    
    
    (cond
      (and (:text message)
           (= (:text message) "/start"))
      (telegram/send-message 
        config
        (-> message :chat :id)
        "Присылайте траты и доходы. Формат указан в описании бота."
        {:reply-markup
         {
          :keyboard
          (keyboard config ds message)}})
      
      (re-matches (:transaction REGEXPS) 
                  (str (:text message)))
      (let [timestamp
              (System/currentTimeMillis)
              
              text
              (:text message)
              
              words
              (str/split text #" ")
              
              amount
              (first words)
              
              agent
              (reduce
                (fn [x y] (format "%s %s" x y))
                (rest words))
              
              amount
              (if 
                (= (first amount) \+)
                (parse-double amount)
                (- (parse-double amount)))]
        
        (sql/insert-row! ds config
          {:chat-id (-> message :chat :id)
           :message-id (:message_id message)
           :timestamp timestamp 
           :agent agent
           :amount amount})
        
        
        
        (ok config ds message)
          
        (when 
          (< amount 0)
          (telegram/send-message
            config
            (-> message :chat :id)
            (format "%s ₽: %s\n\n%.0f ₽ с начала месяца,\n%.0f ₽ из них — %s" 
              amount
              agent
              (sql/gross-of-month ds
                {:chat-id (-> message :chat :id)
                 :timestamp timestamp})
              (-
                (sql/gross-of-month-by-agent ds
                  {:chat-id (-> message :chat :id)
                   :timestamp timestamp
                   :agent agent}))
              agent
              nil)
            {:reply-markup
             {:inline_keyboard
              (inline-keyboard message)}}))
          (when 
            (> amount 0)
            (telegram/send-message
              config
              (-> message :chat :id)
              (format "+ %s ₽: %s"
                amount
                agent)
              {:reply-markup
               {:inline_keyboard
                (inline-keyboard message)}})))
      
      
      (re-matches (:category REGEXPS)
                  (str (:text message)))
      (let [match
            (re-matches (:category REGEXPS)
                  (str (:text message)))
            
            agent
            (second match)
            
            category
            (nth match 2)]
        
        (sql/set-category! ds {:agent agent 
                               :category category
                               :chat-id (-> message :chat :id)})
        (ok config ds message))
      
      
      (some? callback_query)
      (let [callback-message  (:message callback_query)
            callback-data     (parse-long (:data callback_query))
            cb-message-id     (:message_id callback-message)]
        
        (sql/delete-row! ds
          {:chat-id (-> callback-message :chat :id)
           :message-id callback-data})
        (telegram/edit-message-text
          config
          (-> callback-message :chat :id)
          (-> callback-message :message_id)
          "Удалено"
          {:parse-mode "markdown"})))))


(comment
  
  (nth [0 1 2] 2)
  
  (re-matches #"(.*) - (.*)" "кофе - рестораны")
  (re-matches #"\+?([-.0-9]+) ([^\n]+)" "+100")
  
  (hashids/encode
    {:salt (slurp "creds")}
    475396835)
  (parse-double "+150")
  (reduce str (rest (str/split "+150 варя шалина" #" ")))
  )