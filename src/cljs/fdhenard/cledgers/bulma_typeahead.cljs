(ns fdhenard.cledgers.bulma-typeahead
  (:require [cljs.pprint :as pp]
            [clojure.string :as string]
            [reagent.core :as reagent :refer [atom]]))

(defn query-callback [ta-atom is-loading-atom item->text results]
  (reset! ta-atom (merge @ta-atom
                         {:matches results}))
  (reset! is-loading-atom false))


(defn on-typeahead-change! [new-val count-atom ta-atom is-loading-atom query-func item->text]
  (reset! ta-atom (merge
                   @ta-atom
                   {:textbox-val new-val}))
  (swap! count-atom inc)
  (js/setTimeout
   (fn []
     (swap! count-atom dec)
     (when (and (<= @count-atom 0)
                (seq new-val))
       (reset! is-loading-atom true)
       (query-func new-val (partial query-callback ta-atom is-loading-atom item->text))))
   500))


(defn typeahead-textbox [ta-atom query-func item->text]
  (let [change-count-atom (atom 0)
        is-loading-atom (atom false)]
    (fn []
      (println "is-loading:" @is-loading-atom)
      [:div {:class #{:field}}
       [:div {:class #{:control (when @is-loading-atom :is-loading)}}
        [:input {:class #{:input} :type :text :placeholder "something"
                 :value (:textbox-val @ta-atom)
                 :on-change #(let [new-val (-> % .-target .-value)]
                               (on-typeahead-change!
                                new-val
                                change-count-atom
                                ta-atom
                                is-loading-atom
                                query-func
                                item->text))}]]])))

(defn new-typeahead-vals []
  {:textbox-val nil
   :matches #{}
   :selection-val nil})


(defn typeahead-component [{:keys [ta-atom] :as parm-map}]
  (fn [parm-map]
    (let [query-func (:query-func parm-map)
          on-change (:on-change parm-map)
          textbox-val (-> ta-atom deref :textbox-val)
          selection-val (-> ta-atom deref :selection-val)
          dropdown-expanded (not (= textbox-val selection-val))
          item->text (:item->text parm-map)
          allow-new? (:allow-new? parm-map true)]
      [:div {:class #{:dropdown (when dropdown-expanded :is-active)}}
       [:div {:class #{:dropdown-trigger}}
        [typeahead-textbox ta-atom query-func item->text]]
       (when-not (string/blank? textbox-val)
         (let [matches (:matches @ta-atom)
               match-texts (->> matches
                                (map item->text)
                                set)
               has-exact-match (contains? match-texts textbox-val)
               create-new {:id nil
                           :name textbox-val}
               dropdown-vals (if (or has-exact-match
                                     (not allow-new?))
                               matches
                               (conj matches create-new))]
           (when (seq dropdown-vals)
             [:div {:class #{:dropdown-menu} :id :dropdown-menu :role :menu}
              [:div {:class #{:dropdown-content}}
               (for [item dropdown-vals]
                 (let [text (item->text item)
                       id (:id item)]
                   (if id
                     ^{:key id}
                     [:a {:href "#"
                          :class #{:dropdown-item}
                          :on-click (fn [_evt]
                                      (reset! ta-atom (merge
                                                       @ta-atom
                                                       {:textbox-val text
                                                        :selection-val text}))
                                      (on-change {:value text
                                                  :is-new false
                                                  :id id}))}
                      text]
                     ^{:key "new"}
                     [:a {:href "#"
                          :class #{:dropdown-item}
                          :on-click (fn [_evt]
                                      (reset! ta-atom (merge @ta-atom
                                                             {:selection-val textbox-val}))
                                      (on-change {:value textbox-val
                                                  :is-new true
                                                  :id nil}))}
                      (str "create new \"" textbox-val "\"")])))]])))])))
