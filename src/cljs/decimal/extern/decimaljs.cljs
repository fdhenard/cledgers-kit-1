(ns decimal.extern.decimaljs
  (:require ["decimal.js" :as decimal-js]))
(js/goog.exportSymbol "Decimal.noConflict" (fn [] decimal-js))
