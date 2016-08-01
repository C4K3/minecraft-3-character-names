#!/usr/bin/env clj
(require '[clojure.string :as str]
         '[clojure.set :as sets])
(import java.net.URL)

(def characters ["0" "1" "2" "3" "4" "5" "6" "7" "8" "9" "_" "a" "b" "c" "d" "e" "f" "g" "h" "i" "j" "k" "l" "m" "n" "o" "p" "q" "r" "s" "t" "u" "v" "w" "x" "y" "z"])
(def possible-names (for [a characters b characters c characters] (str a b c)))

(defn send-usernames-request [request]
  "Do a Playernames -> UUIDs request"
  (loop []
    (let
      [conn (.. (new URL "https://api.mojang.com/profiles/minecraft") openConnection)]
      (do
        (. conn setRequestMethod "POST")
        (. conn setRequestProperty "Content-Type" "application/json")
        (. conn setDoOutput true)
        (spit (. conn getOutputStream) request))
      (if (not= 200 (int (. conn getResponseCode)))
        (do
          (println "Did not get a 200 response, sleeping for 15 seconds")
          (Thread/sleep 15000)
          (recur))
        ;(slurp (. conn getInputStream))
        (set
          (map
            (fn [x] (second (re-find #"name\":\"(\S+?)\"" (str/lower-case x))))
            (str/split
              (let [resp (slurp (. conn getInputStream))]
                (println "Response:" resp)
                resp)
              #"\},\{"))))
      )))

(defn create-request-payload [names]
  "Create the json payload accepted by the API given a vector of names"
  (str "["
       (reduce
         (fn [a b] (str a ",\"" b "\""))
         (str "\"" (first names) "\"")
         (rest names)
         )
       "]"))

(defn get-all []
  "Get all available-now or soon-available names"
  (loop [available #{} remaining-names possible-names]
    (if (empty? remaining-names)
      available
      (let [names (take 100 remaining-names)]
        (let [response (send-usernames-request (create-request-payload names))
              avail (sets/difference (set names) response)]
          (println "Trying for names:" names)
          (recur (sets/union available avail) (nthrest remaining-names 100)))))))

(defn send-at-time-request [name]
  "Send a Username -> UUID at time request, returns true if name was in use 37 days ago"
  (loop []
    (let
      [past-time (- (quot (System/currentTimeMillis) 1000) (* 60 60 24 37))
       url (str "https://api.mojang.com/users/profiles/minecraft/" name "?at=" past-time)
       conn (.. (new URL url) openConnection)
       response (int (. conn getResponseCode))]
      (cond
        (= response 200) (do
                           (println "Setting" name "to soon-available")
                           true)
        (= response 204) (do
                           (println "Setting" name "to available-now")
                           false)
        :else (do
                (println "Got an error (presumably Too Many Requests,) sleeping for 15 seconds")
                (Thread/sleep 15000)
                (recur))))))

(defn check-all []
  "Check all names, returns two sets of available-now and soon-available"
  (loop [remaining-names (into [] (reverse (sort (get-all))))
         available-now []
         soon-available []]
    (if (empty? remaining-names)
      [available-now soon-available]
      (if (send-at-time-request (peek remaining-names))
        (recur (pop remaining-names)
               available-now
               (conj soon-available (peek remaining-names)))
        (recur (pop remaining-names)
               (conj available-now (peek remaining-names))
               soon-available)))))

(let [res (check-all)
      available-now (first res)
      soon-available (second res)]
  (with-open [soon-writer (clojure.java.io/writer "./soon-available.txt")
              now-writer (clojure.java.io/writer "./available-now.txt")]
    (doall (for [name (sort soon-available)] (. soon-writer write (str name "\n"))))
    (doall (for [name (sort available-now)] (. now-writer write (str name "\n"))))
    ))

