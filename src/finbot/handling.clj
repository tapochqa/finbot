(ns finbot.handling
  (:require
    [tg-bot-api.telegram :as telegram]
    [clojure.string :as str]
    [finbot.sql :as sql]
    [finbot.time :as time]
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


(defn reply
  [ds config message {:keys [amount agent category timestamp sign] :or {sign "<"}}]
  (let [polarity (case sign 
                   "<" - 
                   ">" +)]
    (telegram/send-message
              config
              (-> message :chat :id)
              (format
                "%s ₽: %s\n\n%,d ₽ в %s\n%,d ₽  — %s\n%,d ₽  — %s\n\n%,d ₽ в %s\n%,d ₽  — %s\n%,d ₽  — %s"
                amount
                agent

                (long
                  (sql/gross-of-month ds
                    {:chat-id (-> message :chat :id)
                     :timestamp timestamp
                     :sign sign}))
                (time/which-month timestamp)
                (long
                  (polarity
                    (sql/gross-of-month-by-category ds
                      {:chat-id (-> message :chat :id)
                       :timestamp timestamp
                       :category category
                       :sign sign})))
                category
                (long 
                  (polarity
                    (sql/gross-of-month-by-agent ds
                      {:chat-id (-> message :chat :id)
                       :timestamp timestamp
                       :agent agent
                       :sign sign})))
                agent

               (long
                  (sql/gross-of-year ds
                    {:chat-id (-> message :chat :id)
                     :timestamp timestamp
                     :sign sign}))
                (time/which-year timestamp)
                (long
                  (polarity
                   (sql/gross-of-year-by-category ds
                    {:chat-id (-> message :chat :id)
                     :timestamp timestamp
                     :category category
                     :sign sign})))
                category
                (long
                  (polarity
                    (sql/gross-of-year-by-agent ds
                      {:chat-id (-> message :chat :id)
                       :timestamp timestamp
                       :agent agent
                       :sign sign})))
                agent)
              {:reply-markup
               {:inline_keyboard
                (inline-keyboard message)}})))


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

              category
              (sql/get-category ds {:agent agent
                                    :chat-id (-> message :chat :id)})
              
              amount
              (if 
                (= (first amount) \+)
                (parse-double amount)
                (- (parse-double amount)))]
        
        (sql/deactivate-duplicates ds)

        (sql/insert-row! ds config
          {:chat-id (-> message :chat :id)
           :message-id (:message_id message)
           :timestamp timestamp 
           :agent agent
           :amount amount})
        
        
        (ok config ds message)
          
        (when 
		  (= amount 0.0)
		  (telegram/send-message
		  	config
		  	(-> message :chat :id)
		  	(format "%s: %s с начала месяца"
		  		agent
		  		(sql/gross-of-month-by-range
		  			ds
		  			{:chat-id (-> message :chat :id)
		  			 :agent agent
		  			 :min-amount 0
		  			 :max-amount 0
		  			 :timestamp timestamp}))
		  	{:reply-markup
             {:inline_keyboard
              (inline-keyboard message)}}
		  	))
        
        
        
        
        (when
          (< amount 0)
          (reply ds config message {:amount amount
                                    :agent agent
                                    :category category
                                    :timestamp timestamp
                                    :sign "<"}))

        (when 
          (> amount 0)
          (reply ds config message {:amount amount
                                    :agent agent
                                    :category category
                                    :timestamp timestamp
                                    :sign ">"})
            ))
      
      
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
  
  (format "%,d" (long 12454))
          
  
  (nth [0 1 2] 2)
  
  (re-matches #"(.*) - (.*)" "кофе - рестораны")
  (re-matches #"\+?([-.0-9]+) ([^\n]+)" "+100")
  (long 1.23)
  (hashids/encode
    {:salt (slurp "creds")}
    475396835)
  (parse-double "+150")
  (reduce str (rest (str/split "+150 варя шалина" #" ")))
  )