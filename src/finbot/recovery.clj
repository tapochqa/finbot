(ns finbot.recovery
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.string :as str]
   
   [finbot.config :as config]
   
   [cheshire.core :as json]
   [hashids.core :as hashids]))


(defn keys-select [x y]
  (select-keys y x))


(spec/def ::ne-string
  (spec/and string? not-empty))


(spec/def ::sum 
  (spec/and 
    ::ne-string
    (spec/conformer (fn [s] (parse-double (str/replace s #"," ""))))))


(spec/def ::agent ::ne-string)
(spec/def ::category ::ne-string)


(spec/def ::parsed-text
   (spec/keys :req-un
    [::sum ::agent ::category]))


(spec/def ::reply-text->
  (let [->groups
        (fn [x] (->> x
                  (re-seq #"(?Um)^([\d.,]+)\s*₽\s*[:—-]\s*([^\r\n]+)$")
                  (map rest)
                  flatten
                  (take 4)
                  (zipmap [:sum :agent :kanchik :category])
                  (spec/conform ::parsed-text)))]
    (spec/and 
      (fn [x] (false? (spec/invalid? (->groups x))))
      (spec/conformer
        ->groups
        identity))))


(spec/def ::->unix-with-seconds
  (spec/and 
    string?
    (spec/conformer
      (fn [s] (parse-long (format "%s%d" s (int (* 1000 (rand)))))))))


(spec/def ::text
  (spec/and 
    string?
    ::reply-text->))

(spec/def ::date_unixtime ::->unix-with-seconds)


(spec/def ::messages
  (spec/and
   (spec/keys :req-un [::text
                      ::date_unixtime])
     
   (spec/conformer 
     (fn [x] (assoc x :foo :bar)))))


(defn map-tg-export
  [filename & {:keys [chat_id chat_id_hash]}]
  
  (let [dialog
        (-> filename
          slurp
          (json/parse-string true)
          :messages
          reverse)]
    (->> dialog
      (map (fn [x] (spec/conform ::messages x)))
      (filter map?)
      (map (fn [{:keys [date_unixtime] :as x}] 
             (-> x (select-keys [:text :date_unixtime])
               (assoc 
                    :amount        (get-in x [:text :sum])
            	    :agent         (get-in x [:text :agent])
            	    :category      (get-in x [:text :agent])
            	    :chat_id       chat_id
            	    :chat_id_hash  chat_id_hash
                    :timestamp     date_unixtime
                    :active 1
            	    )
               (dissoc :text :date_unixtime)))))))

  
  








