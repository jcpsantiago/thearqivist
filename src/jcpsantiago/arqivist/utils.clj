(ns jcpsantiago.arqivist.utils)

(defn ts->datetime
  "Convert a UNIX timestamp into a human friendly string."
  [ts tz]
  (let [epoch-seconds (Long/parseLong ts)
        zone (java.time.ZoneId/of tz)
        formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")]
    (-> epoch-seconds
        java.time.Instant/ofEpochSecond
        (.atZone zone)
        (.format formatter))))
