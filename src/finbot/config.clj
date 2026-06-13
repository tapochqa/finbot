(ns finbot.config
  (:require [hashids.core :as hashids]))


(defn make-config
  [token creds & local-server]
  { :test-server false
    :local-server local-server
    :token token
    :polling {:update-timeout 1000}
    :creds creds})


(defn make-hash
  [config chat-id]
  (hashids/encode
    {:salt (:creds config)}
    chat-id))