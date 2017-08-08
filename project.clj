;; Copyright (c) 2014 Engagor
;;
;; The use and distribution terms for this software are covered by the
;; BSD License (http://opensource.org/licenses/BSD-2-Clause)
;; which can be found in the file LICENSE at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(defproject dl4clj "0.0.1"
  :description "ports of some DL4J examples to Clojure"
  :url "https://github.com/engagor/dl4clj"
  :license {:name "BSD C2"
            :url "http://opensource.org/licenses/BSD-2-Clause"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.deeplearning4j/deeplearning4j-core "0.9.0"]
                 ;;no later versions released
                 [org.deeplearning4j/deeplearning4j-ui "0.6.0"]
                 [org.deeplearning4j/deeplearning4j-nlp "0.9.0"]
                 [org.apache.commons/commons-io "1.3.2"]
                 [org.nd4j/nd4j-native "0.9.0"]
                 [org.nd4j/canova-api "0.0.0.14"]
                 [org.clojure/data.json "0.2.6"]])
  
