(ns finbot.core 
  (:gen-class)
  (:require
    [finbot.polling  :as polling]
    [finbot.lambda   :as lambda]
    [finbot.config   :as config]

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
        (config/make-config my-token creds "http://95.215.8.235")]
  (polling/run-polling config)
  #_(lambda config)))


(comment
  
   (binding [*in* (-> "yc-request.json"
                 clojure.java.io/resource
                 clojure.java.io/reader)]
     
     (-main (slurp "token") (slurp "creds")))
  
  
  (-main "...:...")
  
  )
