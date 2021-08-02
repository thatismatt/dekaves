(ns dekaves.hash
  (:import [java.security MessageDigest]
           [java.math BigInteger]))

(defn md5 [^String s]
  (let [algorithm (MessageDigest/getInstance "MD5")
        raw (.digest algorithm (.getBytes s))]
    (BigInteger. 1 raw)))

(comment ;;
()

(first (sort-by second (map (juxt identity md5) (set (repeatedly 1000000 (fn [] (reduce str (repeatedly 5 #(rand-nth "abcdefghijklmnopqrstuvwxyz")))))))))

["whpgr" 363656050942859229201918034496428]
["opuuv" 141978474671467888987155196657738681]
["tkxsp" 381685858207666517702773589315154966]
["boukr" 430323645792811858339693157850415694]
["swjhd" 38570212499474674062886349556193944409]
["aghwt" 22954059164512025009496562496885473465]
["ryyor" 27872041881867480239608250408236201187]

Long/MAX_VALUE

Byte/MAX_VALUE
Byte/MIN_VALUE

)
