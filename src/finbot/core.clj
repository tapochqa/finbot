(ns finbot.core 
  (:gen-class)
  (:require
    [finbot.polling  :as polling]
    [finbot.lambda   :as lambda]
    [clojure.string    :as str]
    [cheshire.core     :as json]))


(defn lambda
  [config]
  (-> (lambda/->request config)
      (lambda/handle-request! config)
      (lambda/response->)))


(defn -main
  [my-token creds]
  
  (let [config 
        { :test-server false
          :token my-token
          :polling {:update-timeout 1000}
          :creds creds
          }]
  (polling/run-polling config)
  #_(lambda config)))


(comment
  
   (binding [*in* (-> "yc-request.json"
                 clojure.java.io/resource
                 clojure.java.io/reader)]
     
     (-main (slurp "token") (slurp "creds")))
  
  
  (-main "...:...")
  
  )
