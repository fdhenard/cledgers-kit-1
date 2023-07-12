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

                    $ lein run -d ../cledgers-kit-1 -b "postgresql://localhost/cledgers?user=cledgers" migrate

            - execute:

                    $ lein run -d ../cledgers-kit-1 -b "postgresql://localhost/cledgers?user=cledgers" -e migrate

    - insert self as user
        - eval the code in the Rich Comment of `fdhenard.cledgers.dev.scripts`

- install nvm `brew install nvm` and the latest LTS version of node

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
cd ~/dev/repos/cledgers-kit-1
npx shadow-cljs watch app
```

##### In emacs

- `M-x cider-connect`
- host: `localhost`
- port: see shadow-cljs output. eg. `shadow-cljs - nREPL server started on port 7002`
- `shadow.user> (shadow/repl :app)`
- test repl?: `(js/alert "Hi")`

### Command Line

Run `clj -M:dev:nrepl` or `make repl`.

Note that, just like with [CIDER](#cider), this alias runs nREPL during development. To run nREPL in production (typically when the system starts), use the kit-nrepl library through the +nrepl profile as described in [the documentation](https://kit-clj.github.io/docs/profiles.html#profiles).

## provision server

### prereqs

- babashka installed

### steps

#### terraform

- put credentials in env vars or in .zshrc

	```
	export AWS_ACCESS_KEY_ID=xxxx
	export AWS_SECRET_ACCESS_KEY=xxxx
	```

- this stuffs
	
	```sh
	brew install tfenv
	cd infrastructure/terraform/prod
	tfenv install
	terraform init
	terraform plan
	terraform apply
	```

#### babashka

- ssh into the server

	- acquire the ip address from aws console
	- do this

	    ```sh
	    ssh -i "Dropbox/Programming/frank-key-pair.pem" ubuntu@999.999.999.999
	    ```
	    
- add machine ssh key to the server `.ssh/authorized_keys`
- execute babashka code in `infrastructure/babashka/src/up.clj`
- run migrations - see dev instructions
