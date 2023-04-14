(ns fdhenard.cledgers.entities
  (:require [clojure.edn :as edn]))

(def entities (-> "resources/declarative-ddl/entities.edn" slurp edn/read-string))
