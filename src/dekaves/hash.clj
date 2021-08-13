(ns dekaves.hash
  (:import [java.security MessageDigest]
           [java.math BigInteger]
           [java.nio.charset Charset]))

(defn md5 [^String s]
  (let [algorithm (MessageDigest/getInstance "MD5")
        raw (.digest algorithm (.getBytes s (Charset/forName "UTF-8")))]
    (BigInteger. 1 raw)))

(defn multi-hash [s n]
  (map #(md5 (str s (format "%04d" %)))
       (range n)))

(defn make-ring [nodes spots redundancy]
  {:pre [(<= redundancy (count nodes))]}
  (->> nodes
       (mapcat (fn [node] (map (fn [h] {:node node :hash h}) (multi-hash node spots))))
       (sort-by :hash)
       (reduce (fn [r e]
            (let [previous-nodes (->> r (map :node) reverse (take (dec redundancy)) set)]
              (if (contains? previous-nodes (:node e)) r (conj r e)))) [])))

(defn ring-lookup [ring id redundancy]
  (let [h (md5 id)]
    (->> (concat (drop-while #(> h (:hash %)) ring) ring)
         (take redundancy)
         (map :node))))

(comment ;;
()

;; spot "loss" caused by ring simplification
(let [nodes   (map #(format "node-%02d" %) [1 2 3 4 5])
      spotss  (->> (range 12)
                   (map #(int (Math/pow 2 %)))
                   (map (fn [spots] {:name (str spots " spots") :value spots})))
      results (map (fn [r]
                     (->> spotss
                          (map (fn [spots]
                                 (let [actual-spots    (count (make-ring nodes (:value spots) r))
                                       max-total-spots (* (count nodes) (:value spots))]
                                   [(:name spots) (str (format "%.2f%%" (float (* 100 (/ actual-spots max-total-spots)))) " " actual-spots)])))
                          (into {:redundancy r})))
                   [0 1 2 3 4])]
  (clojure.pprint/print-table (into [:redundancy] (map :name spotss)) results))
;; | :redundancy |   1 spots |    2 spots |    4 spots |    8 spots |   16 spots |    32 spots |    64 spots |   128 spots |    256 spots |    512 spots |   1024 spots |    2048 spots |
;; |-------------+-----------+------------+------------+------------+------------+-------------+-------------+-------------+--------------+--------------+--------------+---------------|
;; |           1 | 100.00% 5 | 100.00% 10 | 100.00% 20 | 100.00% 40 | 100.00% 80 | 100.00% 160 | 100.00% 320 | 100.00% 640 | 100.00% 1280 | 100.00% 2560 | 100.00% 5120 | 100.00% 10240 |
;; |           2 | 100.00% 5 |   80.00% 8 |  90.00% 18 |  87.50% 35 |  80.00% 64 |  80.00% 128 |  79.06% 253 |  77.19% 494 |  79.53% 1018 |  80.23% 2054 |  79.49% 4070 |   80.31% 8224 |
;; |           3 | 100.00% 5 |   70.00% 7 |  65.00% 13 |  62.50% 25 |  60.00% 48 |   54.38% 87 |  55.31% 177 |  57.03% 365 |   59.45% 761 |  60.90% 1559 |  60.12% 3078 |   60.69% 6215 |
;; |           4 | 100.00% 5 |   50.00% 5 |  50.00% 10 |  45.00% 18 |  42.50% 34 |   30.00% 48 |  38.75% 124 |  37.97% 243 |   39.06% 500 |  39.92% 1022 |  41.00% 2099 |   40.76% 4174 |

;; testing ring-lookup
(let [nodes      (map #(format "node-%02d" %) [1 2 3 4])
      redundancy 2
      ring       (make-ring nodes 4 redundancy)
      ids        ["a" "b" "c" "w"]] ;; (> (md5 "w") (md5 "node-040002"))
  (sort-by :hash
           (concat ring
                   (map #(let [h     (md5 %)
                               nodes (ring-lookup ring % redundancy)]
                           {:hash h :nodes nodes :id %})
                        ids))))
;; ({:node "node-02", :hash 8212020063772473014023425740190974188}
;;  {:hash 16955237001963240173058271559858726497, :nodes ("node-01" "node-03"), :id "a"}
;;  {:node "node-01", :hash 21719301674096972454044499684110739456}
;;  {:node "node-03", :hash 92840847202495517398057101260820755418}
;;  {:node "node-04", :hash 93565992659155728722738987404924959195}
;;  {:hash 99079589977253916124855502156832923443, :nodes ("node-03" "node-02"), :id "c"}
;;  {:node "node-03", :hash 142772928329377850351993635115749835888}
;;  {:node "node-02", :hash 152467949803122543999999915533288317654}
;;  {:hash 195289424170611159128911017612795795343, :nodes ("node-01" "node-04"), :id "b"}
;;  {:node "node-01", :hash 197517659240158136582785557846356223414}
;;  {:node "node-04", :hash 236459746486839840370361458522703608723}
;;  {:node "node-02", :hash 305683436437771107250715275424751756149}
;;  {:node "node-03", :hash 310795251817661982306461874865065705222}
;;  {:node "node-04", :hash 319010951100769761208976357465054502959}
;;  {:hash 320556862105665356727910535058133114216, :nodes ("node-02" "node-01"), :id "w"})

;; data movement caused by failover
(let [nodes-a (map #(format "node-%02d" %) [1 2 3 4])
      nodes-b (map #(format "node-%02d" %) [1 2 3])
      nodes-c (map #(format "node-%02d" %) [1 2 3 5])
      n       512
      r       2
      ring-a  (make-ring nodes-a n r)
      ring-b  (make-ring nodes-b n r)
      ring-c  (make-ring nodes-c n r)
      ids     (map #(format "id-%08d" %) (range 10000))
      overlap (fn [xs ys] (seq (clojure.set/intersection (set xs) (set ys))))]
  (frequencies
   (map (fn [id]
          (let [[na nb nc] (map (fn [ring] (ring-lookup ring id r))
                                [ring-a ring-b ring-c])]
            (cond-> 2 (overlap na nb) dec (overlap nb nc) dec)))
        ids)))
;; {0 9336, 2 164, 1 500}

;; distribution across nodes
(let [nodes       (map #(format "node-%02d" %) [1 2 3 4 5])
      n           512
      r           3
      ring        (make-ring nodes n r)
      ids         (map #(format "id-%08d" %) (range 10000))
      allocations (map (fn [id] (ring-lookup ring id r)) ids)]
  [(frequencies (map first allocations))
   (frequencies (map second allocations))
   (frequencies (map set allocations))])
;; [{"node-01" 2072, "node-04" 2032, "node-02" 1883, "node-03" 2073, "node-05" 1940}
;;  {"node-02" 2136, "node-03" 1902, "node-04" 2097, "node-05" 1980, "node-01" 1885}
;;  {#{"node-05" "node-02" "node-04"} 917
;;   #{"node-05" "node-03" "node-04"} 1075
;;   #{"node-05" "node-01" "node-04"} 965
;;   #{"node-02" "node-03" "node-04"} 1080
;;   #{"node-05" "node-01" "node-03"} 850
;;   #{"node-01" "node-03" "node-04"} 928
;;   #{"node-02" "node-01" "node-03"} 1130
;;   #{"node-02" "node-01" "node-04"} 1123
;;   #{"node-05" "node-02" "node-01"} 1027
;;   #{"node-05" "node-02" "node-03"} 905}]

;; multi hashing the id vs ring simplification (and using the "next" spot)
(let [nodes         (map #(format "node-%02d" %) [1 2 3 4 5])
      n             512
      redundancy    3
      ring-1        (make-ring nodes n 1)
      ring-2        (make-ring nodes n redundancy)
      ids           (map #(format "id-%08d" %) (range 10000))
      allocations-1 (map (fn [id] (mapcat #(ring-lookup ring-1 (format (str id "-%03d") %) 1) (range redundancy))) ids)
      allocations-2 (map (fn [id] (ring-lookup ring-2 id redundancy)) ids)]
  [(sort (frequencies (map (comp count set) allocations-1)))
   (sort (frequencies (map (comp count set) allocations-2)))])
;; [([1 410] [2 4831] [3 4759]) ;; multi hashing
;;  ([3 10000])] ;; ring simplification

)
