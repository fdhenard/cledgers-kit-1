(ns fdhenard.cledgers.components
  (:require [reagent.core :as r]
            [cljs-time.core :as time]))

(defn editable-date-component [{:keys [on-change
                                       value]}]
  (let [month (r/atom (time/month value))
        day (r/atom (time/day value))
        year (r/atom (time/year value))
        on-blur
        (fn [_evt]
          (let [new-date
                (time/local-date
                 (js/parseInt @year)
                 (js/parseInt @month)
                 (js/parseInt @day))]
            (on-change new-date)))]
    (fn []
      [:div
       [:input {:type "text"
                :size 2
                :value @month
                :on-change
                (fn [evt]
                  (reset! month (-> evt .-target .-value)))
                :onBlur on-blur}]
       [:span "/"]
       [:input {:type "text"
                :size 2
                :value @day
                :on-change
                (fn [evt]
                  (reset! day (-> evt .-target .-value)))
                :onBlur on-blur}]
       [:span "/"]
       [:input {:type "text"
                :size 4
                :value @year
                :on-change
                (fn [evt]
                  (reset! year (-> evt .-target .-value)))
                :onBlur on-blur}]])))
