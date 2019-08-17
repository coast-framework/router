(ns router.core
  (:require [clojure.string :as string]
            [clojure.repl :as repl]))


(def routes-atom (atom nil))
(def middleware-atom (atom nil))


(defn verb? [value]
  (contains? #{:get :post :put :patch :delete :head :connect :trace}
    value))


(defn route? [val]
  (and (vector? val)
       (verb? (first val))
       (string? (second val))
       (fn? (nth val 2))))


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
  (let [routes (filter #(not (fn? %)) args)
        fns (filter fn? args)
        f (apply comp fns)
        flattened-routes (flatten-wrapped-routes routes)
        routes-with-middleware (->> (mapv drop-last flattened-routes)
                                    (mapv vec)
                                    (mapv #(vector % f))
                                    (into {}))]
    (swap! middleware-atom (partial merge-with comp) routes-with-middleware)
    flattened-routes))


(defn app
  "Creates a ring handler from routes"
  [routes]
  (fn [request]
    (let [{:keys [uri params]} request
          route (route request routes)
          [route-method route-uri handler] route
          middleware (get @middleware-atom [route-method route-uri])
          handler (if (fn? middleware)
                    (middleware handler)
                    handler)
          route-params (route-params uri route-uri)
          request (assoc request :params (merge params route-params))]
      (when (some? handler)
        (handler request)))))


(defn apps
  "Runs multiple router handlers until one of them matches a route"
  [& handlers]
  (fn [request]
    (some #(% request) handlers)))


(defn concat-route [a b]
  (distinct (concat a b)))


(defn keywordize [f]
  (-> f str repl/demunge (string/split #"@") first keyword))


(defn route-name [route]
  (or (get route 3)
   (keywordize (get route 2))))


(defn routes [& args]
  (let [flat-routes (flatten-wrapped-routes args)
        route-map (->> flat-routes
                       (mapv #(vector (route-name %) %))
                       (into {}))]
    (swap! routes-atom merge route-map)
    flat-routes))


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
  ([route-keyword]
   (url-for route-keyword {}))
  ([route-keyword params]
   (let [route (get @routes-atom route-keyword)
         url (route-str (nth route 1) params)
         query-params (get params :?)
         anchor (get params :#)
         anchor (if (some? anchor) (str "#" anchor) "")]
     (str url (query-string query-params) anchor))))


(defn action-for
  ([route-keyword]
   (action-for route-keyword {}))
  ([route-keyword params]
   (let [[method route-url] (get @routes-atom route-keyword)
         action (route-str route-url params)
         _method method
         method (if (not= :get method) :post :get)]
     {:method method
      :_method _method
      :action action})))


(defn redirect-to [& args]
  {:status 302
   :body ""
   :headers {"Location" (apply url-for args)}})


(defn prefix-route [s route]
  (if (>= (count route) 3)
    (update route 1 #(str s %))
    route))


(defn prefix [s & routes]
  (mapv (partial prefix-route s) routes))
