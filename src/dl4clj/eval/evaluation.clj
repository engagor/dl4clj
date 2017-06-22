(ns ^{:doc "implementation of the eval class in dl4j.  Used to get performance metrics for a model
see: https://deeplearning4j.org/doc/org/deeplearning4j/eval/Evaluation.html and
https://deeplearning4j.org/doc/org/deeplearning4j/eval/RegressionEvaluation.html"}
    dl4clj.eval.evaluation
  (:import [org.deeplearning4j.eval Evaluation RegressionEvaluation BaseEvaluation])
  (:require [dl4clj.utils :refer [contains-many? generic-dispatching-fn get-labels]]
            [nd4clj.linalg.api.ds-iter :refer [has-next? next-example!]]
            [dl4clj.nn.multilayer.multi-layer-network :refer [output]]
            [nd4clj.linalg.dataset.api.data-set :refer [get-features]]
            [dl4clj.helpers :refer [reset-if-empty?! new-lazy-iter]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; multimethod for creating the evaluation java object
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti evaler generic-dispatching-fn)

(defmethod evaler :classification [opts]
  (let [conf (:classification opts)
        {labels :labels
         top-n :top-n
         l-to-i-map :label-to-idx
         n-classes :n-classes} conf]
    (cond (contains-many? conf :labels :top-n)
          (Evaluation. labels top-n)
          (contains? conf :labels)
          (Evaluation. (into '() labels))
          (contains? conf :label-to-idx)
          (Evaluation. l-to-i-map)
          (contains? conf :n-classes)
          (Evaluation. n-classes)
          :else
          (Evaluation.))))

(defmethod evaler :regression [opts]
  (let [conf (:regression opts)
        {column-names :column-names
         precision :precision
         n-columns :n-columns} conf
        c-names (into '() column-names)]
    (cond (contains-many? conf :column-names :precision)
          (RegressionEvaluation. c-names precision)
          (contains-many? conf :n-columns :precision)
          (RegressionEvaluation. n-columns precision)
          (contains? conf :column-names)
          (RegressionEvaluation. c-names)
          (contains? conf :n-columns)
          (RegressionEvaluation. n-columns)
          :else
          (assert
           false
           "you must supply either the number of columns or their names for regression evaluation"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; user facing fns
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn new-classification-evaler
  "Creates an instance of an evaluation object which reports precision, recall, f1

   :labels (coll), a collection of string labels to use for the output

   :top-n (int), value to use for the top N accuracy calc.
     - An example is considered correct if the probability for the true class
       is one of the highest n values

   :n-classes (int), the number of classes to account for in the evaluation

   :label-to-idx (map), {column-idx (int) label (str)}
    - another way to set the labels for the classification"
  [& {:keys [labels top-n label-to-idx n-classes]
      :as opts}]
  (evaler {:classification opts}))

(defn new-regression-evaler
  "Evaluation method for the evaluation of regression algorithms.

   provides MSE, MAE, RMSE, RSE, correlation coefficient for each column

   :column-names (coll), a collection of string naming the columns

   :precision (int), specified precision to be used

   :n-columns (int), the number of columns in the dataset"
  [& {:keys [column-names precision n-columns]
      :as opts}]
  (evaler {:regression opts}))

(defn eval-classification!
  ;; add doc string
  [& {:keys [labels network-predictions mask-array record-meta-data evaler
             mln features predicted-idx actual-idx]
      :as opts}]
  (assert (contains? opts :evaler) "you must provide an evaler to evaluate a classification task")
  (cond (contains-many? opts :labels :network-predictions :record-meta-data)
        (doto evaler (.eval labels network-predictions (into '() record-meta-data)))
        (contains-many? opts :labels :network-predictions :mask-array)
        (doto evaler (.eval labels network-predictions mask-array))
        (contains-many? opts :labels :features :mln)
        (doto evaler (.eval labels features mln))
        (contains-many? opts :labels :network-predictions)
        (doto evaler (.eval labels network-predictions))
        (contains-many? opts :predicted-idx :actual-idx)
        (doto evaler (.eval predicted-idx actual-idx))
        :else
        (assert false "you must supply an evaler, the correct labels and the network predicted labels")))

(defn get-stats
  "Method to obtain the classification report as a String"
  [& {:keys [evaler suppress-warnings?]
      :as opts}]
  (if (contains? opts :suppress-warnings?)
    (.stats evaler suppress-warnings?)
    (.stats evaler)))

(defn eval-model-whole-ds
  "evaluate the model performance on an entire data set and print the final result

  :mln (multi layer network), a trained mln you want to get classification stats for

  :eval-obj (evaler), the object created by new-classification-evaler

  :iter (iter), the dataset iterator which has the data you want to evaluate the model on

  :lazy-data (lazy-seq), a lazy sequence of dataset objects

  you should supply either a dl4j dataset-iterator (:iter) or a lazy-seq (:lazy-data), not both

  returns the evaluation object"
  [& {:keys [mln eval-obj iter lazy-data]
      :as opts}]
  (let [ds-iter (if (contains? opts :lazy-data)
                  (new-lazy-iter lazy-data)
                  (reset-if-empty?! iter))]
    (while (has-next? ds-iter)
      (let [nxt (next-example! ds-iter)
            prediction (output :mln mln :input (get-features nxt))]
        (eval-classification!
         :evaler eval-obj
         :labels (get-labels nxt)
         :network-predictions prediction))))
  (println (get-stats :evaler eval-obj))
  eval-obj)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; classification evaluator interaction fns
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-accuracy
  "Accuracy: (TP + TN) / (P + N)"
  [evaler]
  (.accuracy evaler))

(defn add-to-confusion
  "Adds to the confusion matrix"
  [& {:keys [evaler real-value guess-value]}]
  (doto evaler
    (.addToConfusion (int real-value) (int guess-value))))

(defn class-count
  "Returns the number of times the given label has actually occurred"
  [& {:keys [evaler class-label-idx]}]
  (.classCount evaler (int class-label-idx)))

(defn confusion-to-string
  "Get a String representation of the confusion matrix"
  [evaler]
  (.confusionToString evaler))

(defn f1
  "TP: true positive FP: False Positive FN: False Negative
  F1 score = 2 * TP / (2TP + FP + FN),

  the calculation will only be done for a single class if that classes idx is supplied
   -here class refers to the labels the network was trained on"
  [& {:keys [evaler class-label-idx]
      :as opts}]
  (if (contains? opts :class-label-idx)
    (.f1 evaler (int class-label-idx))
    (.f1 evaler)))

(defn false-alarm-rate
  "False Alarm Rate (FAR) reflects rate of misclassified to classified records"
  [evaler]
  (.falseAlarmRate evaler))

(defn false-negative-rate
  "False negative rate based on guesses so far Takes into account all known classes
  and outputs average fnr across all of them

  can be scoped down to a single class if class-label-idx supplied"
  [& {:keys [evaler class-label-idx edge-case]
      :as opts}]
  (cond (contains-many? opts :class-label-idx :edge-case :evaler)
        (.falseNegativeRate evaler (int class-label-idx) edge-case)
        (contains-many? opts :evaler :class-label-idx)
        (.falseNegativeRate evaler (int class-label-idx))
        (contains? opts :evaler)
        (.falseNegativeRate evaler)
        :else
        (assert false "you must atleast provide an evaler to get the false negative rate of the model being evaluated")))

(defn false-negatives
  "False negatives: correctly rejected"
  [evaler]
  (.falseNegatives evaler))

(defn false-positive-rate
  "False positive rate based on guesses so far Takes into account all known classes
  and outputs average fpr across all of them

  can be scoped down to a single class if class-label-idx supplied"
  [& {:keys [evaler class-label-idx edge-case]
      :as opts}]
  (cond (contains-many? opts :class-label-idx :edge-case :evaler)
        (.falsePositiveRate evaler (int class-label-idx) edge-case)
        (contains-many? opts :evaler :class-label-idx)
        (.falsePositiveRate evaler (int class-label-idx))
        (contains? opts :evaler)
        (.falsePositiveRate evaler)
        :else
        (assert false "you must atleast provide an evaler to get the false positive rate of the model being evaluated")))

(defn false-positives
  "False positive: wrong guess"
  [evaler]
  (.falsePositives evaler))

(defn get-class-label
  "get the class a label is associated with given an idx"
  [& {:keys [evaler label-idx]}]
  (.getClassLabel evaler (int label-idx)))

(defn get-confusion-matrix
  "Returns the confusion matrix variable"
  [evaler]
  (.getConfusionMatrix evaler))

(defn get-num-row-counter
  [evaler]
  (.getNumRowCounter evaler))

(defn get-prediction-by-predicted-class
  "Get a list of predictions, for all data with the specified predicted class,
  regardless of the actual data class."
  [& {:keys [evaler idx-of-predicted-class]}]
  (.getPredictionByPredictedClass evaler (int idx-of-predicted-class)))

(defn get-prediction-errors
  "Get a list of prediction errors, on a per-record basis"
  [evaler]
  (.getPredictionErrors evaler))

(defn get-predictions
  "Get a list of predictions in the specified confusion matrix entry
  (i.e., for the given actua/predicted class pair)"
  [& {:keys [evaler actual-class-idx predicted-class-idx]}]
  (.getPredictions evaler actual-class-idx predicted-class-idx))

(defn get-predictions-by-actual-class
  "Get a list of predictions, for all data with the specified actual class,
  regardless of the predicted class."
  [& {:keys [evaler actual-class-idx]}]
  (.getPredictionsByActualClass evaler actual-class-idx))

(defn get-top-n-correct-count
  "Return the number of correct predictions according to top N value."
  [evaler]
  (.getTopNCorrectCount evaler))

(defn get-top-n-total-count
  "Return the total number of top N evaluations."
  [evaler]
  (.getTopNTotalCount evaler))

(defn increment-false-negatives!
  [& {:keys [evaler class-label-idx]}]
  (doto evaler
    (.incrementFalseNegatives (int class-label-idx))))

(defn increment-false-positives!
  [& {:keys [evaler class-label-idx]}]
  (doto evaler
    (.incrementFalsePositives (int class-label-idx))))

(defn increment-true-negatives!
  [& {:keys [evaler class-label-idx]}]
  (doto evaler
    (.incrementTrueNegatives (int class-label-idx))))

(defn increment-true-positives!
  [& {:keys [evaler class-label-idx]}]
  (doto evaler
    (.incrementTruePositives (int class-label-idx))))

(defn total-negatives
  "Total negatives true negatives + false negatives"
  [evaler]
  (.negative evaler))

(defn total-positives
  "Returns all of the positive guesses: true positive + false negative"
  [evaler]
  (.positive evaler))

(defn get-precision
  "Precision based on guesses so far Takes into account all known classes and
  outputs average precision across all of them.

  can be scoped to a label given its idx"
  [& {:keys [evaler class-label-idx edge-case]
      :as opts}]
  (cond (contains-many? opts :class-label-idx :edge-case :evaler)
        (.precision evaler (int class-label-idx) edge-case)
        (contains-many? opts :evaler :class-label-idx)
        (.precision evaler (int class-label-idx))
        (contains? opts :evaler)
        (.precision evaler)
        :else
        (assert false "you must atleast provide an evaler to get the precision of the model being evaluated")))

(defn recall
  "Recall based on guesses so far Takes into account all known classes
  and outputs average recall across all of them

  can be scoped to a label given its idx"
  [& {:keys [evaler class-label-idx edge-case]
      :as opts}]
  (cond (contains-many? opts :class-label-idx :edge-case :evaler)
        (.recall evaler (int class-label-idx) edge-case)
        (contains-many? opts :evaler :class-label-idx)
        (.recall evaler (int class-label-idx))
        (contains? opts :evaler)
        (.recall evaler)
        :else
        (assert false "you must atleast provide an evaler to get the recall of the model being evaluated")))

(defn top-n-accuracy
  "Top N accuracy of the predictions so far."
  [evaler]
  (.topNAccuracy evaler))

(defn true-negatives
  "True negatives: correctly rejected"
  [evaler]
  (.trueNegatives evaler))

(defn true-positives
  "True positives: correctly rejected"
  [evaler]
  (.truePositives evaler))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; interact with a regression evaluator
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-mean-squared-error
  "returns the MSE"
  [& {:keys [regression-evaler column-idx]}]
  (.meanSquaredError regression-evaler column-idx))

(defn get-mean-absolute-error
  "returns MAE"
  [& {:keys [regression-evaler column-idx]}]
  (.meanAbsoluteError regression-evaler column-idx))

(defn get-root-mean-squared-error
  "returns rMSE"
  [& {:keys [regression-evaler column-idx]}]
  (.rootMeanSquaredError regression-evaler column-idx))

(defn get-correlation-r2
  "return the R2 correlation"
  [& {:keys [regression-evaler column-idx]}]
  (.correlationR2 regression-evaler column-idx))

(defn get-relative-squared-error
  "return relative squared error"
  [& {:keys [regression-evaler column-idx]}]
  (.relativeSquaredError regression-evaler column-idx))
