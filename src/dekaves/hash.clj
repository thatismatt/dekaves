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

(defn make-ring [nodes spots]
  (->> nodes
       (mapcat (fn [node] (map (fn [h] [h node]) (multi-hash node spots))))
       (sort-by first)
       (reduce (fn [r e] (if (-> r last second (= (second e))) r (conj r e))) [])))

(defn ring-lookup [ring id redundancy]
  (let [h (md5 id)]
    (->> (concat (drop-while #(> h (first %)) ring) ring)
         (take redundancy)
         (map second))))

(comment ;;
()

(let [nodes (map #(format "node-%02d" %) [1 2 3 4])
      ns    (map #(int (Math/pow 2 %)) (range 12))]
  (map (fn [n] (format "%.2f%%" (float (* 100 (/ (count (make-ring nodes n)) (* 4 n)))))) ns))
;; ("100.00%" "75.00%" "68.75%" "84.38%" "73.44%" "74.22%" "73.44%" "71.29%" "74.02%" "74.17%" "74.71%" "75.85%")

(let [nodes (map #(format "node-%02d" %) [1 2 3 4])
      ring  (make-ring nodes 4)]
  ring)
;; [[8212020063772473014023425740190974188 "node-02"]
;;-  16955237001963240173058271559858726497 ;; "a"
;;  [21719301674096972454044499684110739456 "node-01"]
;;  [92840847202495517398057101260820755418 "node-03"]
;;  [93565992659155728722738987404924959195 "node-04"]
;;-  99079589977253916124855502156832923443 ;; "c"
;;  [142772928329377850351993635115749835888 "node-03"]
;;  [152467949803122543999999915533288317654 "node-02"]
;;-  195289424170611159128911017612795795343 ;; "b"
;;  [197517659240158136582785557846356223414 "node-01"]
;;  [236459746486839840370361458522703608723 "node-04"]
;;  [305683436437771107250715275424751756149 "node-02"]
;;  [310795251817661982306461874865065705222 "node-03"]
;;  [319010951100769761208976357465054502959 "node-04"]] ;; "node-040002"

(let [nodes (map #(format "node-%02d" %) [1 2 3 4])
      ring  (make-ring nodes 4)
      ids ["a" "b" "c" "w"]] ;; (> (md5 "w") (md5 "node-040002"))
  (map #(ring-lookup ring % 3) ids))
;; (("node-01" "node-03") ("node-01" "node-04") ("node-03" "node-02") ("node-02" "node-01"))

(let [nodes-a (map #(format "node-%02d" %) [1 2 3 4])
      nodes-b (map #(format "node-%02d" %) [1 2 3])
      nodes-c (map #(format "node-%02d" %) [1 2 3 5])
      n       512
      ring-a  (make-ring nodes-a n)
      ring-b  (make-ring nodes-b n)
      ring-c  (make-ring nodes-c n)
      ids     (map #(format "id-%08d" %) (range 10000))
      overlap (fn [xs ys] (seq (clojure.set/intersection (set xs) (set ys))))]
  (frequencies
   (map (fn [id]
          (let [[na nb nc] (map (fn [ring] (ring-lookup ring id 2))
                                [ring-a ring-b ring-c])]
            (cond (and (overlap na nb) (overlap nb nc)) :no-move
                  (or (overlap na nb) (overlap nb nc)) :one-move
                  :else :two-moves)))
        ids)))
;; {:no-move 9336, :two-moves 164, :one-move 500}

)
