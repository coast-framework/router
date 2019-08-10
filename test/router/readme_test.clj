(ns router.readme-test
  (:require [router.core :as router]
            [clojure.test :refer [deftest is testing]]))

(defn account-index [request])
(defn account-show [request])
(defn account-create [request])
(defn account-update [request])
(defn home-index [request]
  {:status 200
   :body "Yes"})

(defn authenticate [handler]
  (fn [request]
    (if (some? (:session request))
      (handler request)
      {:status 403 :body "No"})))

(def private-routes
  (router/routes
    [:get "/secrets" account-index]
    [:get "/secrets/:id" account-show]
    [:post "/secrets" account-create]
    [:put "/secrets/:id" account-update]))

(def public-routes
  (router/routes
    [:get "/home" home-index]))

(def private-app (-> (router/app private-routes)
                     (authenticate)))

(def public-app (router/app public-routes))

(def app (router/apps public-app private-app))

(deftest apps-test
  (testing "middleware example from README"
    (is (= {:status 403 :body "No"}
           (app {:request-method :get :uri "/secrets"}))))

  (testing "middleware example without middleware from README"
    (is (= {:status 200 :body "Yes"}
           (app {:request-method :get :uri "/home"})))))
