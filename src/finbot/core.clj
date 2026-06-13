(ns finbot.core 
  (:gen-class)
  (:require
    [finbot.polling  :as polling]
    [finbot.lambda   :as lambda]
    [clojure.string    :as str]
    [cheshire.core     :as json]
    [org.httpkit.client :as http]
   ))


(defn lambda
  [config]
  (-> (lambda/->request config)
      (lambda/handle-request! config)
      (lambda/response->)))


(defn -main
  [my-token creds]
  
  (let [config 
        { :test-server false
          :local-server "http://109.238.95.58:8081"
          :token my-token
          :polling {:update-timeout 1000}
          :creds creds
          :salt (slurp "salt")
          }]
  (polling/run-polling config)
  #_(lambda config)))


(comment
  
  (defn test-telegram []
  (println "Testing Telegram API through proxy...")
  @(http/get "http://api.telegram.org/botYOUR_TOKEN/getMe"
     {:proxy {:host "127.0.0.1" :port 10829 :type :socks5}
      :timeout 30000}))
  (test-telegram)
   (binding [*in* (-> "yc-request.json"
                 clojure.java.io/resource
                 clojure.java.io/reader)]
     
     (-main (slurp "token") (slurp "creds")))
  
  
  (-main "...:..." 23)
  
  )
