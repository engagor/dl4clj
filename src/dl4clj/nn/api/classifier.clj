(ns ^{:doc "Implementation of the methods found in the Classifier Interface.
fns are for classification models (this is for supervised learning)
see: https://deeplearning4j.org/doc/org/deeplearning4j/nn/api/Classifier.html"}
  dl4clj.nn.api.classifier
  (:import [org.deeplearning4j.nn.api Classifier])
  (:require [dl4clj.utils :refer [contains-many?]]
            [dl4clj.helpers :refer [reset-if-empty?!]]))

(defn f1-score
  "With two arguments (classifier and dataset):
   - Sets the input and labels and returns a score for the prediction.

  With three arguments (classifier, examples and labels):
   - Returns the f1 score for the given examples.
   - examples and labels should both be INDArrays
    - examples = the data you want to classify
    - labels = the correct classifcation for a a set of examples"
  [& {:keys [classifier dataset examples labels]
      :as opts}]
  (cond (contains? opts :dataset)
        (.f1Score classifier dataset)
        (contains-many? opts :examples :labels)
        (.f1Score classifier examples labels)
        :else
        (assert false "you must supply a classifier and either a dataset or
examples and their labels")))

(defn fit-classifier!
  "If dataset-iterator is supplied, trains the classifier based on the dataset-iterator
  if dataset or examples and labels are supplied, fits the classifier.

  :data-set = a dataset

  :iter (iterator), an iterator for going through a collection of dataset objects

  :examples = INDArray of input data to be classified

  :labels = INDArray or integer-array of labels for the examples

  Returns the classifier after it has been fit"
  [& {:keys [classifier data-set iter examples labels]
      :as opts}]
  (cond (contains? opts :data-set)
        (doto classifier (.fit data-set))
        (contains? opts :dataset-iterator)
        (doto classifier (.fit (reset-if-empty?! iter)))
        (contains-many? opts :examples :labels)
        (doto classifier (.fit examples labels))
        :else
        (assert false "you must supply a classifier and either a dataset,
 iterator obj, or examples and their labels")))

(defn label-probabilities
  "Returns the probabilities for each label for each example row wise

  :examples (INDArray), the examples to classify (one example in each row)"
  [& {:keys [classifier examples]}]
  (.labelProbabilities classifier examples))

(defn num-labels
  "Returns the number of possible labels"
  [classifier]
  (.numLabels classifier))

(defn predict
  "Takes in a list of examples for each row (INDArray), returns a label

   or

  takes a datset of examples for each row, returns a label"
  [& {:keys [classifier examples dataset]
      :as opts}]
  (cond (contains? opts :examples) (.predict classifier examples)
        (contains? opts :dataset) (.predict classifier dataset)
        :else
        (assert false "you must supply a classifier and either an INDArray of examples or a dataset")))
