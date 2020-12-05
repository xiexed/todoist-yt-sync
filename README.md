# todoist-sync

A Clojure library designed to ... well, that part is up to you.

## Usage

    lein ring server-headless

## Start in dev-mode

`-Dconfig.file=application-dev.conf`

```clojure
(require '[todoist-sync.core :refer :all])
(require '[ring.adapter.jetty :as jt])
(require '[ring.middleware.reload :refer [wrap-reload]])
(def server (jt/run-jetty (wrap-reload #'app) {:port 3000 :join? false}))
```

## Development hints

Getting from session
```clojure
(def yt-token (->> (deref session-atom) (vals) (first) (:ring.middleware.oauth2/access-tokens) (:youtrack) (:token)))
```

## License

Copyright © 2020 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
