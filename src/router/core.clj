(ns router.core
  (:require [clojure.string :as string]))


(defn verb? [value]
  (contains? #{:get :post :put :patch :delete :head :connect :trace}
    value))


(defn route? [val]
  (and (vector? val)
       (verb? (first val))
       (string? (second val))
       (or (fn? (nth val 2))
           (keyword? (nth val 2)))))


(defn qualify-ident [k]
  (when (and (ident? k)
             (re-find #"-" (name k)))
    (let [[kns kn] (string/split (name k) #"-")]
      (keyword (or kns "") (or kn "")))))


(defn replacement [match m]
  (let [fallback (first match)
        k (-> match last keyword)
        s1 (get m k)
        s2 (get m (qualify-ident k))]
    (str (or s1 s2 fallback))))


(defn route-str [s m]
  (when (and (string? s)
             (or (nil? m) (map? m)))
    (string/replace s #":([\w-_]+)" #(replacement % m))))


(defn symbolize [k]
  (if (keyword? k)
    (symbol (namespace k) (name k))
    k))


(defn resolve-safely [sym]
  (when (symbol? sym)
    (resolve sym)))


(defn resolve-route [val]
  (cond
    (keyword? val) (-> (symbolize val)
                       (resolve-safely))
    (symbol? val) (resolve-safely val)
    (fn? val) val
    :else nil))


(defn params [s]
  (->> (re-seq #":([\w-_]+)" s)
       (map last)
       (map keyword)))


(defn pattern [s]
  (->> (string/replace s #":([\w-_]+)" "([A-Za-z0-9-_~]+)")
       (re-pattern)))


(defn route-params [req-uri route-uri]
  (when (every? string? [req-uri route-uri])
    (let [ks (params route-uri)
          param-pattern (pattern route-uri)]
      (->> (re-seq param-pattern req-uri)
           (first)
           (drop 1)
           (zipmap ks)))))


(defn match [request route]
  (let [{:keys [request-method uri]} request
        [route-method route-uri] route
        params (route-params uri route-uri)
        route-uri (route-str route-uri params)]
    (and (= request-method route-method)
         (or (= uri route-uri)
             (= (str uri "/") route-uri)))))


(defn exact-match [request route]
  (let [{:keys [request-method uri]} request
        [route-method route-uri] route]
    (and (= request-method route-method)
         (= uri route-uri))))


(defn route [request routes]
  (or
   (-> (filter #(exact-match request %) routes)
       (first))
   (-> (filter #(match request %) routes)
       (first))))


(defn depth
  ([val]
   (depth val 0))
  ([val idx]
   (if (sequential? val)
     (depth (first val) (inc idx))
     idx)))


(defn flatten-wrapped-routes [x]
  (if (> (depth x) 1)
    (vec
     (mapcat flatten-wrapped-routes x))
    [x]))


(defn middleware [& args]
  (let [fns (filter fn? args)
        vectors (->> (filter vector? args)
                     (flatten-wrapped-routes))]
    (mapv #(vec (concat % fns)) vectors)))


(defn middleware-fn [route]
  (->> (drop 3 route)
       (apply comp)))


(defn app
  "Creates a ring handler from routes"
  [routes]
  (fn [request]
    (let [{:keys [uri params]} request
          route (route request routes)
          [_ route-uri route-keyword] route
          route-handler (resolve-route route-keyword)
          route-params (route-params uri route-uri)
          route-middleware (middleware-fn route)
          request (assoc request :params (merge params route-params)
                                 :route route-keyword)
          handler (route-middleware route-handler)]
      (when (some? handler)
        (handler request)))))


(defn routes [& routes]
  (flatten-wrapped-routes routes))


(defn url-encode [s]
  (when (string? s)
    (-> (java.net.URLEncoder/encode s "UTF-8")
        (.replace "+" "%20")
        (.replace "*" "%2A")
        (.replace "%7E" "~"))))


(defn query-string [m]
  (let [s (->> (map (fn [[k v]] (str (-> k name url-encode) "=" (url-encode v))) m)
               (string/join "&"))]
    (when (not (string/blank? s))
      (str "?" s))))


(defn url-for
  ([routes route-keyword]
   (url-for routes route-keyword {}))
  ([routes route-keyword params]
   (let [route (-> (filter #(= (nth % 2) route-keyword) routes)
                   (first))
         url (route-str (nth route 1) params)
         route-params (route-params url (nth route 1))
         query-params (-> (apply dissoc params (keys route-params))
                          (dissoc :#))
         anchor (get params :#)
         anchor (if (some? anchor) (str "#" anchor) "")]
     (str url (query-string query-params) anchor))))


(defn action-for
  ([routes route-keyword]
   (action-for routes route-keyword {}))
  ([routes route-keyword params]
   (let [[method route-url] (-> (filter #(= (nth % 2) route-keyword) routes)
                                (first))
         action (route-str route-url params)
         _method method
         method (if (not= :get method)
                  :post
                  :get)]
     {:method method
      :_method _method
      :action action})))


(defn redirect-to [routes & args]
  {:status 302
   :body ""
   :headers {"Location" (apply (partial url-for routes) args)}})


(defn prefix-route [s route]
  (if (>= (count route) 3)
    (update route 1 #(str s %))
    route))


(defn prefix [s & routes]
  (mapv (partial prefix-route s) routes))
