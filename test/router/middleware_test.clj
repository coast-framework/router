(ns router.middleware-test
  (:require [router.core :as router]
            [clojure.test :refer [deftest is testing]]))

(defn mw-secrets [request])
(defn mw-secrets-show [request])
(defn mw-secrets-create [request])
(defn mw-secrets-update [request])

(defn authenticate [handler]
  (fn [request]
    (if (some? (:session request))
      (handler request)
      {:status 403 :body "No"})))


(defn mw1 [handler]
  (fn [request]
    (handler (assoc request :mw1 true))))


(defn mw2 [handler]
  (fn [request]
    (handler (assoc request :mw2 true))))


(def routes
  (router/routes
    (router/middleware authenticate
      [:get "/mw-secrets" mw-secrets]
      [:get "/mw-secrets/:id" mw-secrets-show]
      [:post "/mw-secrets" mw-secrets-create]
      [:put "/mw-secrets/:id" mw-secrets-update])

    (router/middleware mw1
      (router/middleware mw2
        [:get "/mw-public" (fn [request] request)]))))

(def app (router/app routes))

(deftest middleware-test
  (testing "middleware example"
    (is (= (app {:request-method :get :uri "/mw-secrets"})
           {:status 403 :body "No"})))

  (testing "no middleware example"
    (is (map? (app {:request-method :get :uri "/mw-public"}))))

  (testing "two middleware example"
    (let [response (app {:request-method :get :uri "/mw-public"})]
      (is (true? (every? response [:mw1 :mw2]))))))
