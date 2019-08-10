# router
Easy clojure routing

## Installation

Add this lib to your `deps.edn`

```clojure
{:deps {coast-framework/router {:git/url "https://github.com/coast-framework/router"
                                :sha "29d1108775eb3ab7fe7ba4eb60325d5090dcdae4"}}}
```

## Usage

Require it like this

```clojure
(ns your-project
  (:require [router.core :as router]))
```

Use it like this

```clojure
(defn index [request]
  {:status 200
   :body "index"
   :headers {"Content-Type" "text/plain"}})


(defn post [request]
  {:status 200
   :body "post"
   :headers {"Content-Type" "text/plain"}})


(defn account [request]
  (let [{:keys [params]} request]
    {:status 200
     :body (str "account with id " (:id params))
     :headers {"Content-Type" "text/plain"}}))


(def routes
  (router/routes
    [:get "/" index :index]
    [:post "/posts" post :post]
    [:get "/accounts/:id" account :account]))
```

## Middleware

The router supports per-route ring middleware

```clojure
(defn mw1 [handler]
  (fn [request]
    (handler request)))

(defn mw2 [handler]
  (fn [request]
    (handler request)))

(def routes
  (router/routes
    [:get "/" index]
    (router/middleware mw1 mw2
      [:get "/posts" post]
      [:get "/accounts/:id" account])))
```

## Combining Handlers

In some cases it might make more sense to keep separate apps separate and combine them later
instead of defining middleware functions across route vectors.

```clojure
(defn authenticate [handler]
  (fn [request]
    (if (some? (:session request))
      (handler request)
      {:status 403 :body "No"})))

(def private-routes
  (router/routes
    [:get "/accounts" account/index]
    [:get "/accounts/:id" account/show]
    [:post "/accounts" account/create]
    [:put "/accounts/:id" account/update]))

(def public-routes
  (router/routes
    [:get "/" home/index]))

(def private-app (-> (router/app private-routes)
                     (authenticate)))

(def public-app (router/app public-routes))

(def app (router/apps public-app private-app))
```

### Prefix

You can also prefix a set of routes like so

```clojure
(def api-routes (router/routes
                 (router/prefix "/api"
                  [:get "/" routes.api/index :api/index] ; => GET /api
                  [:post "/" routes.api/post :api/index]))) ; => POST /api
```


### Helpers

The `url-for` helper takes a route name and returns the string for that route

```clojure
(router/url-for :index) ; => "/"
(router/url-for :hello) ; => "/hello"
(router/url-for :account/show {:id 1}) ;=> "/accounts/1"
```

The `action-for` helper takes a route name and optional args and returns a map for forms.
The `put`, `patch` and `delete` in forms are all set under the `_method` key.

```clojure
(router/action-for :account/create) ; => {:method :post :action "/accounts"}
(router/action-for :account/update {:id 2}) ; => {:method :post :action "/accounts/2" :_method :put}
```

The `redirect-to` helper takes a route name and optional args and
returns a ring response map with the `"Location"` header set

```clojure
(router/redirect-to :account/show {:id 1}) ; => {:status 302 :body "" :headers {"Location" "/accounts/1"}}
(router/redirect-to :index) ; =>  ; => {:status 302 :body "" :headers {"Location" "/"}}
```

## Testing

```sh
cd router && make test
```

## License

MIT

## Contribution

Any and all issues or pull requests welcome!
