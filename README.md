# cledgers

[toc]

## kit project creation (generation) notes

```shell
clj -Ttools install com.github.seancorfield/clj-new '{:git/tag "v1.2.399"}' :as clj-new

clojure -Tclj-new create :template io.github.kit-clj :name fdhenard/cledgers :args '[+sql]'
mv cledgers cledgers-kit-1
```

in repl

```
(kit/sync-modules)
(kit/install-module :kit/cljs)
```

## dev prereqs

- db setup
    - postgresql installed
        - use homebrew to install and use `$ brew services start postgresql`
        - if first time installing, you may need to run `$ createdb` to create the db with username
    - `$ psql postgres`
    - `> create role cledgers with createdb login;`
    - exit psql `^d`
    - `$ createdb cledgers -O cledgers`
    - run migrations
        - see [readme for DDDL](https://github.com/fdhenard/declarative-ddl)
        - as of 6/16/2021
            - `$ cd ~/dev/repos/declarative-ddl`
            - dry run

                    $ lein run -d ../cledgers-luminus -b "postgresql://localhost/cledgers_luminus?user=cledgers_luminus" migrate

            - execute:

                    $ lein run -d ../cledgers-luminus -b "postgresql://localhost/cledgers_luminus?user=cledgers_luminus" -e migrate

    - insert self as user
        - eval the code in the Rich Comment of `fdhenard.cledgers.dev.scripts`

## REPLs

### CIDER

```sh
clj -M:dev:cider
```

Use the `cider` alias for CIDER nREPL support (run `clj -M:dev:cider`). See the [CIDER docs](https://docs.cider.mx/cider/basics/up_and_running.html) for more help.

Note that this alias runs nREPL during development. To run nREPL in production (typically when the system starts), use the kit-nrepl library through the +nrepl profile as described in [the documentation](https://kit-clj.github.io/docs/profiles.html#profiles).

Start the server with:

```clojure
(go)
```

The default API is available under http://localhost:3000/api

System configuration is available under `resources/system.edn`.

To reload changes:

```clojure
(reset)
```

#### Clojurescript

```sh
npx shadow-cljs watch app
```

### Command Line

Run `clj -M:dev:nrepl` or `make repl`.

Note that, just like with [CIDER](#cider), this alias runs nREPL during development. To run nREPL in production (typically when the system starts), use the kit-nrepl library through the +nrepl profile as described in [the documentation](https://kit-clj.github.io/docs/profiles.html#profiles).
