(ns fdhenard.cledgers.bulma-typeahead
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.pprint :as pp]))

(defn query-callback [matches-atom is-loading-atom item->text results]
  ;; (println "calling callback")
  ;; (pp/pprint results)
  (do
    (reset! matches-atom results)
    (reset! is-loading-atom false)))


(defn on-typeahead-change! [new-val count-atom value-atom matches-atom is-loading-atom query-func item->text]
  (do
    (reset! value-atom new-val)
    (swap! count-atom inc)
    (js/setTimeout
     (fn []
       (swap! count-atom dec)
       #_(println "count-atom val in timeout cb:" @count-atom)
       (when (and (<= @count-atom 0)
                  (not (empty? new-val)))
         (reset! is-loading-atom true)
         #_(println "calling-exec-query! for query =" new-val)
         (query-func new-val (partial query-callback matches-atom is-loading-atom item->text))))
     500)))


(defn typeahead-textbox [value-atom matches-atom query-func item->text]
  (let [change-count-atom (atom 0)
        is-loading-atom (atom false)]
    (fn []
      (println "is-loading:" @is-loading-atom)
      [:div {:class #{:field}}
       [:div {:class #{:control (when @is-loading-atom :is-loading)}}
        [:input {:class #{:input} :type :text :placeholder "something"
                 :value @value-atom
                 :on-change #(let [new-val (-> % .-target .-value)
                                   #_ (println "new-val:" new-val)]
                               (on-typeahead-change!
                                new-val
                                change-count-atom
                                value-atom
                                matches-atom
                                is-loading-atom
                                query-func
                                item->text))}]]])))

#_(defn create-callback [response])

(defn typeahead-component [parm-map]
  (let [#_ (println "rendering typeahead-component")
        #_ (println "app-state")
        #_ (pp/pprint @app-state)
        #_ (pp/pprint parm-map)
        textbox-val-atom (atom (:value parm-map))
        matches-atom (atom #{})
        selection-val-atom (atom nil)]
    (fn [parm-map]
      (let [value (:value parm-map)
            query-func (:query-func parm-map)
            on-change (:on-change parm-map)
            dropdown-expanded (not (= @textbox-val-atom @selection-val-atom))
            item->text (:item->text parm-map)]
        [:div {:class #{:dropdown (when dropdown-expanded :is-active)}}
         [:div {:class #{:dropdown-trigger}}
          [typeahead-textbox textbox-val-atom matches-atom query-func item->text]]
         [:div {:class #{:dropdown-menu} :id :dropdown-menu :role :menu}
          [:div {:class #{:dropdown-content}}
           (let [matches @matches-atom
                 #_ (println "matches:")
                 #_ (pp/pprint matches)
                 textbox-val @textbox-val-atom
                 match-texts (->> matches
                                  (map item->text)
                                  set)
                 has-exact-match (contains? match-texts textbox-val)
                 create-new {:id nil
                             :name textbox-val}
                 dropdown-vals (if has-exact-match
                                 matches
                                 (conj matches create-new))]
             (for [item dropdown-vals]
               (let [text (item->text item)
                     #_ (println "text:" text)
                     id (:id item)]
                 (if id
                   ^{:key id}
                   [:a {:href "#"
                        :class #{:dropdown-item}
                        :on-click (fn [evt]
                                    (do
                                      #_(println "setting selection-val-atom to" text)
                                      (reset! textbox-val-atom text)
                                      (reset! selection-val-atom text)
                                      (on-change {:value text
                                                  :is-new false
                                                  :id id})))}
                    text]
                   ^{:key "new"}
                   [:a {:href "#"
                        :class #{:dropdown-item}
                        :on-click (fn [evt]
                                    (do
                                      (reset! selection-val-atom textbox-val)
                                      (on-change {:value textbox-val
                                                  :is-new true
                                                  :id nil})))}
                    (str "create new \"" textbox-val "\"")]))))]]]))))
