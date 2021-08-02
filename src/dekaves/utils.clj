(ns dekaves.utils)

(defmacro do-1 [e1 & es]
  `(let [result# ~e1] ~@es result#))

(defmacro do-2 [e1 e2 & es]
  `(do ~e1 (let [result# ~e2] ~@es result#)))

(comment ;;
()

(do-2 :a :b :c :d)

(do-1 :a :b :c)

)
