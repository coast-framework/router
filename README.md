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
(def routes (router/routes
              [:get "/" ::index]
              [:post "/" ::post]
              [:get "/accounts/:id"]))

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
```

## Middleware

The coast router supports per-route ring middleware as well

```clojure
(defn authenticate [handler]
  (fn [request]
    (if (some? (:session request))
      (handler request)
      {:status 403 :body "No"})))

(def routes (router/routes
              (router/middleware authenticate
                [:get "/" ::index]
                [:get "/accounts/:id" :account/show]
                [:post "/accounts" :account/create]
                [:put "/accounts/:id" :account/update]

              ; this one is not wrapped
              [:get "/hello" ::hello])))
```

You can also prefix a set of routes like so

```clojure
(def api-routes (router/routes
                 (router/prefix "/api"
                  [:get "/" :routes.api/index] ; => GET /api
                  [:post "/" :routes.api/post]))) ; => POST /api
```

### Helpers

The `url-for` helper takes a route name and returns the string for that route

```clojure
(def url-for (partial router/url-for routes))

(url-for ::index) ; => "/"
(url-for ::hello) ; => "/hello"
(url-for :account/show {:id 1}) ;=> "/accounts/1"
```

The `action-for` helper takes a route name and optional args and returns a map for forms.
The `put`, `patch` and `delete` in forms are all set under the `_method` key.

```clojure
(def action-for (partial router/action-for routes))

(action-for :account/create) ; => {:method :post :action "/accounts"}
(action-for :account/update {:id 2}) ; => {:method :post :action "/accounts/2" :_method :put}
```

The `redirect-to` helper takes a route name and optional args and
returns a ring response map with the `"Location"` header set

```clojure
(def redirect-to (partial router/redirect-to routes))

(redirect-to :account/show {:id 1}) ; => {:status 302 :body "" :headers {"Location" "/accounts/1"}}
(redirect-to ::index) ; =>  ; => {:status 302 :body "" :headers {"Location" "/"}}
```

## Testing

```sh
cd router && make test
```

## License

MIT

## Contribution

Any and all issues or pull requests welcome!
