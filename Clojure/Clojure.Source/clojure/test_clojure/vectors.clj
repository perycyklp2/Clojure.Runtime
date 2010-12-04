﻿;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

; Author: Stuart Halloway, Daniel Solano Gómez

(ns clojure.test-clojure.vectors
  (:use clojure.test))

(deftest test-reversed-vec
  (let [r (range 6)
        v (into (vector-of :int) r)
        reversed (.rseq v)]
    (testing "returns the right impl"
      (is (= clojure.lang.APersistentVector+RSeq (class reversed))))                ;;; clojure.lang.APersistentVector$RSeq 
    (testing "RSeq methods"
      (is (= [5 4 3 2 1 0] reversed))
      ;;;(is (= 5 (.index reversed)))  I think I have the index reversed in RSeq
      (is (= 5 (.first reversed)))
      (is (= [4 3 2 1 0] (.next reversed)))
      (is (= [3 2 1 0] (.. reversed next next)))
      (is (= 6 (.count reversed))))
    (testing "clojure calling through"
      (is (= 5 (first reversed)))
      (is (= 5 (nth reversed 0))))
    (testing "empty reverses to nil"
      (is (nil? (.. v empty rseq))))))

(deftest test-vecseq
  (let [r (range 100)
        vs (into (vector-of :long) r)   ;; was :int
        vs-1 (next vs)
        vs-32 (.chunkedNext (seq vs))]
    (testing "="
      (are [a b] (= a b)
           vs vs
           vs-1 vs-1
           vs-32 vs-32)
      (are [a b] (not= a b)
           vs vs-1
           vs-1 vs
           vs vs-32
           vs-32 vs))
    (testing "IPersistentCollection.empty"
      (are [a] (identical? clojure.lang.PersistentList/EMPTY (.empty (seq a)))
           vs vs-1 vs-32))
    (testing "IPersistentCollection.cons"
      (are [result input] (= result (.cons input :foo))
           [:foo 1] (seq (into (vector-of :int) [1]))))
    (testing "IPersistentCollection.count"
      (are [ct s] (= ct (.count (seq s)))
           100 vs
           99 vs-1
           68 vs-32)
      ;; can't manufacture this scenario: ASeq defers to Counted, but
      ;; LazySeq doesn't, so Counted never gets checked on reified seq below
      #_(testing "hops to counted when available"
        (is (= 200
               (.count (concat
                        (seq vs)
                        (reify clojure.lang.ISeq
                               (seq [this] this)
                               clojure.lang.Counted
                               (count [_] 100))))))))
    (testing "IPersistentCollection.equiv"
      (are [a b] (true? (.equiv a b))
           vs vs
           vs-1 vs-1
           vs-32 vs-32
           vs r)
      (are [a b] (false? (.equiv a b))
           vs vs-1
           vs-1 vs
           vs vs-32
           vs-32 vs
           vs nil))))

(deftest test-vec-compare
  (let [nums      (range 1 100)
        ; randomly replaces a single item with the given value
        rand-replace  (fn[val]
                        (let [r (rand-int 99)]
                          (concat (take r nums) [val] (drop (inc r) nums))))
        ; all num sequences in map
        num-seqs      {:standard       nums
                       :empty          '()
                       ; different lengths
                       :longer         (concat nums [100])
                       :shorter        (drop-last nums)
                       ; greater by value
                       :first-greater  (concat [100] (next nums))
                       :last-greater   (concat (drop-last nums) [100])
                       :rand-greater-1 (rand-replace 100)
                       :rand-greater-2 (rand-replace 100)
                       :rand-greater-3 (rand-replace 100)
                       ; lesser by value
                       :first-lesser   (concat [0] (next nums))
                       :last-lesser    (concat (drop-last nums) [0])
                       :rand-lesser-1  (rand-replace 0)
                       :rand-lesser-2  (rand-replace 0)
                       :rand-lesser-3  (rand-replace 0)}
        ; a way to create compare values based on num-seqs
        create-vals   (fn[base-val]
                        (zipmap (keys num-seqs)
                                (map #(into base-val %1) (vals num-seqs))))
        ; Vecs made of int primitives
        int-vecs      (create-vals (vector-of :int))
        ; Vecs made of long primitives
        long-vecs     (create-vals (vector-of :long))
        ; standard boxing vectors
        regular-vecs  (create-vals [])
        ; the standard int Vec for comparisons
        int-vec       (:standard int-vecs)]
    (testing "compare"
      (testing "identical"
        (is (= 0 (compare int-vec int-vec))))
      (testing "equivalent"
        (are [x y] (= 0 (compare x y))
             ; standard
             int-vec (:standard long-vecs)
             (:standard long-vecs) int-vec
             int-vec (:standard regular-vecs)
             (:standard regular-vecs) int-vec
             ; empty
             (:empty int-vecs) (:empty long-vecs)
             (:empty long-vecs) (:empty int-vecs)))
      (testing "lesser"
        (are [x] (= -1 (compare int-vec x))
             (:longer int-vecs)
             (:longer long-vecs)
             (:longer regular-vecs)
             (:first-greater int-vecs)
             (:first-greater long-vecs)
             (:first-greater regular-vecs)
             (:last-greater int-vecs)
             (:last-greater long-vecs)
             (:last-greater regular-vecs)
             (:rand-greater-1 int-vecs)
             (:rand-greater-1 long-vecs)
             (:rand-greater-1 regular-vecs)
             (:rand-greater-2 int-vecs)
             (:rand-greater-2 long-vecs)
             (:rand-greater-2 regular-vecs)
             (:rand-greater-3 int-vecs)
             (:rand-greater-3 long-vecs)
             (:rand-greater-3 regular-vecs))
        (are [x] (= -1 (compare x int-vec))
             nil
             (:empty int-vecs)
             (:empty long-vecs)
             (:empty regular-vecs)
             (:shorter int-vecs)
             (:shorter long-vecs)
             (:shorter regular-vecs)
             (:first-lesser int-vecs)
             (:first-lesser long-vecs)
             (:first-lesser regular-vecs)
             (:last-lesser int-vecs)
             (:last-lesser long-vecs)
             (:last-lesser regular-vecs)
             (:rand-lesser-1 int-vecs)
             (:rand-lesser-1 long-vecs)
             (:rand-lesser-1 regular-vecs)
             (:rand-lesser-2 int-vecs)
             (:rand-lesser-2 long-vecs)
             (:rand-lesser-2 regular-vecs)
             (:rand-lesser-3 int-vecs)
             (:rand-lesser-3 long-vecs)
             (:rand-lesser-3 regular-vecs)))
      (testing "greater"
        (are [x] (= 1 (compare int-vec x))
             nil
             (:empty int-vecs)
             (:empty long-vecs)
             (:empty regular-vecs)
             (:shorter int-vecs)
             (:shorter long-vecs)
             (:shorter regular-vecs)
             (:first-lesser int-vecs)
             (:first-lesser long-vecs)
             (:first-lesser regular-vecs)
             (:last-lesser int-vecs)
             (:last-lesser long-vecs)
             (:last-lesser regular-vecs)
             (:rand-lesser-1 int-vecs)
             (:rand-lesser-1 long-vecs)
             (:rand-lesser-1 regular-vecs)
             (:rand-lesser-2 int-vecs)
             (:rand-lesser-2 long-vecs)
             (:rand-lesser-2 regular-vecs)
             (:rand-lesser-3 int-vecs)
             (:rand-lesser-3 long-vecs)
             (:rand-lesser-3 regular-vecs))
        (are [x] (= 1 (compare x int-vec))
             (:longer int-vecs)
             (:longer long-vecs)
             (:longer regular-vecs)
             (:first-greater int-vecs)
             (:first-greater long-vecs)
             (:first-greater regular-vecs)
             (:last-greater int-vecs)
             (:last-greater long-vecs)
             (:last-greater regular-vecs)
             (:rand-greater-1 int-vecs)
             (:rand-greater-1 long-vecs)
             (:rand-greater-1 regular-vecs)
             (:rand-greater-2 int-vecs)
             (:rand-greater-2 long-vecs)
             (:rand-greater-2 regular-vecs)
             (:rand-greater-3 int-vecs)
             (:rand-greater-3 long-vecs)
             (:rand-greater-3 regular-vecs))))
    (testing "Comparable.compareTo"
      (testing "incompatible"
        (is (thrown? InvalidCastException (.CompareTo int-vec nil)))                  ;;; NullPointerException  .compareTo
        (are [x] (thrown? InvalidCastException (.CompareTo int-vec x))                    ;;; ClassCastException  .compareTo
             '()
             {}
             #{}
             (sorted-set)
             (sorted-map)
             nums
             1))
      (testing "identical"
        (is (= 0 (.CompareTo int-vec int-vec))))                            ;;; .compareTo
      (testing "equivalent"
        (are [x] (= 0 (.CompareTo int-vec x))                               ;;; .compareTo
             (:standard long-vecs)
             (:standard regular-vecs)))
      (testing "lesser"
        (are [x] (= -1 (.CompareTo int-vec x))                               ;;; .compareTo
             (:longer int-vecs)
             (:longer long-vecs)
             (:longer regular-vecs)
             (:first-greater int-vecs)
             (:first-greater long-vecs)
             (:first-greater regular-vecs)
             (:last-greater int-vecs)
             (:last-greater long-vecs)
             (:last-greater regular-vecs)
             (:rand-greater-1 int-vecs)
             (:rand-greater-1 long-vecs)
             (:rand-greater-1 regular-vecs)
             (:rand-greater-2 int-vecs)
             (:rand-greater-2 long-vecs)
             (:rand-greater-2 regular-vecs)
             (:rand-greater-3 int-vecs)
             (:rand-greater-3 long-vecs)
             (:rand-greater-3 regular-vecs)))
      (testing "greater"
        (are [x] (= 1 (.CompareTo int-vec x))                   ;;; .compareTo
             (:empty int-vecs)
             (:empty long-vecs)
             (:empty regular-vecs)
             (:shorter int-vecs)
             (:shorter long-vecs)
             (:shorter regular-vecs)
             (:first-lesser int-vecs)
             (:first-lesser long-vecs)
             (:first-lesser regular-vecs)
             (:last-lesser int-vecs)
             (:last-lesser long-vecs)
             (:last-lesser regular-vecs)
             (:rand-lesser-1 int-vecs)
             (:rand-lesser-1 long-vecs)
             (:rand-lesser-1 regular-vecs)
             (:rand-lesser-2 int-vecs)
             (:rand-lesser-2 long-vecs)
             (:rand-lesser-2 regular-vecs)
             (:rand-lesser-3 int-vecs)
             (:rand-lesser-3 long-vecs)
             (:rand-lesser-3 regular-vecs))))))

(deftest test-vec-associative
  (let [empty-v (vector-of :long)
        v       (into empty-v (range 1 6))]
    (testing "Associative.containsKey"
      (are [x] (.containsKey v x)
           0 1 2 3 4)
      (are [x] (not (.containsKey v x))
           -1 -100 nil [] "" #"" #{} 5 100)
      (are [x] (not (.containsKey empty-v x))
           0 1))
    (testing "contains?"
      (are [x] (contains? v x)
           0 2 4)
      (are [x] (not (contains? v x))
           -1 -100 nil "" 5 100)
      (are [x] (not (contains? empty-v x))
           0 1))
    (testing "Associative.entryAt"
      (are [idx val] (= (clojure.lang.MapEntry. idx val)
                        (.entryAt v idx))
           0 1
           2 3
           4 5)
      (are [idx] (nil? (.entryAt v idx))
           -5 -1 5 10 nil "")
      (are [idx] (nil? (.entryAt empty-v idx))
           0 1))))
