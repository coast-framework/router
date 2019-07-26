(ns router.core-test
  (:require [router.core :as router]
            [clojure.test :refer [deftest is testing]]))


(deftest app-test
  (let [routes (router/routes
                [:get "/" (fn [request] "GET /")]
                [:get "/accounts/:id" (fn [request] (str "GET /accounts " (-> request :params :id)))]
                [:post "/accounts" (fn [request] "POST /accounts")]
                [:get "/conflict" (fn [request] "GET first /conflict")]
                [:get "/conflict" (fn [request] "GET second /conflict")]
                [:get "/conflict/:param" (fn [request] (str "GET /conflict with param " (-> request :params :param)))]
                [:get "/conflict/test" (fn [request] "GET /conflict/test")]
                (router/middleware (fn [handler] (fn [request] (handler (assoc request :message "with middleware"))))
                  [:get "/middleware" (fn [request] (str "GET /middleware " (:message request)))])
                (router/prefix "/api"
                  [:get "/accounts/:id" (fn [request] (str "GET /api/accounts " (-> request :params :id)))]))
        app (router/app routes)]

    (testing "basic routing without params"
      (is (= "GET /" (app {:request-method :get :uri "/"}))))

    (testing "basic routing with params"
      (is (= "GET /accounts 1" (app {:request-method :get :uri "/accounts/1"}))))

    (testing "prefixed routes"
      (is (= "GET /api/accounts 1" (app {:request-method :get :uri "/api/accounts/1"}))))

    (testing "routes with middleware"
      (is (= "GET /middleware with middleware" (app {:request-method :get :uri "/middleware"}))))

    (testing "conflicting routes"
      (is (= "GET first /conflict" (app {:request-method :get :uri "/conflict"}))))

    (testing "conflicting routes with params"
      (is (= "GET /conflict with param test1" (app {:request-method :get :uri "/conflict/test1"}))))

    (testing "exact match matches over :params routes"
      (is (= "GET /conflict/test" (app {:request-method :get :uri "/conflict/test"}))))))


(deftest url-for-test
 (let [routes (router/routes
               [:get "/" ::home]
               [:post "/" ::home-action]
               [:get "/hello" ::hello]
               [:get "/hello/:id" ::hello-id])]
   (testing "url-for without a map"
     (is (= "/" (router/url-for routes ::home))))

   (testing "url-for with a map with no url params"
     (is (= "/hello?key=value" (router/url-for routes ::hello {:key "value"}))))

   (testing "url-for with a map with url params"
     (is (= "/hello/1?key=value" (router/url-for routes ::hello-id {:id 1 :key "value"}))))

   (testing "url-for with a map, a url param and a #"
     (is (= "/hello/2?key=value#anchor" (router/url-for routes ::hello-id {:id 2 :key "value" :# "anchor"}))))))


(deftest action-for-test
  (let [routes (router/routes
                [:get "/" ::home]
                [:post "/hello" ::hello]
                [:put "/hello/:id" ::hello-id])]

     (testing "action-for without a map"
       (is (= {:method :post :action "/hello" :_method :post} (router/action-for routes ::hello))))

     (testing "action-for with a map and a simulated verb and a param"
       (is (= {:method :post :action "/hello/1" :_method :put} (router/action-for routes ::hello-id {:id 1}))))))


(deftest redirect-to-test
  (let [routes (router/routes
                [:get "/" ::home]
                [:get "/:id" ::params])]

     (testing "redirect-to without a map"
       (is (= {:status 302 :body "" :headers {"Location" "/"}} (router/redirect-to routes ::home))))

     (testing "redirect-to with a map"
       (is (= {:status 302 :body "" :headers {"Location" "/about"}} (router/redirect-to routes ::params {:id "about"}))))))
