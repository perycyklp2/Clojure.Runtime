﻿;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

; Author: Stuart Halloway

(ns clojure.test-clojure.protocols
  (:use clojure.test clojure.test-clojure.protocols.examples)
  (:require [clojure.test-clojure.protocols.more-examples :as other])
  (:import [clojure.test-clojure.protocols.examples ExampleInterface]))           ;;; test_clojure

(defn causes
  [^Exception throwable]                                                 ;;; Throwable
  (loop [causes []
         t throwable]
    (if t (recur (conj causes t) (.InnerException t)) causes)))         ;;; .getCause 

;; this is how I wish clojure.test/thrown? worked...
;; Does body throw expected exception, anywhere in the .getCause chain?
(defmethod assert-expr 'fails-with-cause?
  [msg [_ exception-class msg-re & body :as form]]
  `(try
   ~@body
   (report {:type :fail, :message ~msg, :expected '~form, :actual nil})
   (catch Exception t#                                                           ;;; Throwable
     (if (some (fn [cause#]
                 (and
                  (= ~exception-class (class cause#))
                  (re-find ~msg-re (.Message cause#))))                          ;;; .getMessage
               (causes t#))
       (report {:type :pass, :message ~msg,
                :expected '~form, :actual t#})
       (report {:type :fail, :message ~msg,
                :expected '~form, :actual t#})))))

;; temporary hack until I decide how to cleanly reload protocol
(defn reload-example-protocols
  []
  (alter-var-root #'clojure.test-clojure.protocols.examples/ExampleProtocol
                  assoc :impls {})
  (alter-var-root #'clojure.test-clojure.protocols.more-examples/SimpleProtocol
                  assoc :impls {})
  (require :reload
           'clojure.test-clojure.protocols.examples
           'clojure.test-clojure.protocols.more-examples))

(defn method-names
  "return sorted list of method names on a class"
  [c]
  (->> (.GetMethods c)                       ;;; getMethods
     (map #(.Name %))                        ;;; getName
     (sort)))

(defrecord TestRecord [a b])
(defn r
  ([a b] (TestRecord. a b))
  ([a b meta ext] (TestRecord. a b meta ext)))
(defrecord MapEntry [k v]
  clojure.lang.IMapEntry        ;;; java.util.Map$Entry
  (key [_] k)                   ;;; (getKey [_] k)
  (val [_] v))                  ;;; (getValue [_] v))

(deftest protocols-test
  (testing "protocol fns throw IllegalArgumentException if no impl matches"
    (is (thrown-with-msg?
          ArgumentException               ;;; IllegalArgumentException
          #"No implementation of method: :foo of protocol: #'clojure.test-clojure.protocols.examples/ExampleProtocol found for class: Int32"  ;;; java.lang.Integer
          (foo 10))))
  (testing "protocols generate a corresponding interface using _ instead of - for method names"
    (is (= ["bar" "baz" "baz" "foo" "with_quux"] (method-names clojure.test_clojure.protocols.examples.ExampleProtocol))))
  (testing "protocol will work with instances of its interface (use for interop, not in Clojure!)"
    (let [obj (proxy [clojure.test_clojure.protocols.examples.ExampleProtocol] []
                (foo [] "foo!"))]
      (is (= "foo!" (.foo obj)) "call through interface")
      (is (= "foo!" (foo obj)) "call through protocol")))
  (testing "you can implement just part of a protocol if you want"
    (let [obj (reify ExampleProtocol
                     (baz [a b] "two-arg baz!"))]
      (is (= "two-arg baz!" (baz obj nil)))
      (is (thrown? NotImplementedException (baz obj))))))    ;;; AbstractMethodError
      
(deftype ExtendTestWidget [name])
(deftype HasProtocolInline []
  ExampleProtocol
  (foo [this] :inline))
(deftest extend-test
  (testing "you can extend a protocol to a class"
    (extend String ExampleProtocol
            {:foo identity})
    (is (= "pow" (foo "pow"))))
  (testing "you can have two methods with the same name. Just use namespaces!"
    (extend String other/SimpleProtocol
     {:foo (fn [s] (.ToUpper s))})                   ;;; toUpperCase
    (is (= "POW" (other/foo "pow"))))
  (testing "you can extend deftype types"
    (extend
     ExtendTestWidget
     ExampleProtocol
     {:foo (fn [this] (str "widget " (.name this)))})
    (is (= "widget z" (foo (ExtendTestWidget. "z"))))))

(deftest illegal-extending
  (testing "you cannot extend a protocol to a type that implements the protocol inline"
    (is (fails-with-cause? ArgumentException #".*HasProtocolInline already directly implements"         ;;; IllegalArgumentException,  took out work 'interface' at end of regex
          (eval '(extend clojure.test-clojure.protocols.HasProtocolInline
                         clojure.test-clojure.protocols.examples/ExampleProtocol
                         {:foo (fn [_] :extended)})))))
  (testing "you cannot extend to an interface"
    (is (fails-with-cause? ArgumentException #"clojure.test_clojure.protocols.examples.ExampleProtocol is not a protocol"    ;;; IllegalArgumentException,  took out work 'interface' at beginning of regex
          (eval '(extend clojure.test-clojure.protocols.HasProtocolInline
                         clojure.test_clojure.protocols.examples.ExampleProtocol
                         {:foo (fn [_] :extended)}))))))


(deftype ExtendsTestWidget []
  ExampleProtocol)
(deftest extends?-test
  (reload-example-protocols)
  (testing "returns false if a type does not implement the protocol at all"
    (is (false? (extends? other/SimpleProtocol ExtendsTestWidget))))
  (testing "returns true if a type implements the protocol directly" ;; semantics changed 4/15/2010
    (is (true? (extends? ExampleProtocol ExtendsTestWidget))))
	  (testing "returns true if a type explicitly extends protocol"
		(extend
		 ExtendsTestWidget
		 other/SimpleProtocol
		 {:foo identity})
		(is (true? (extends? other/SimpleProtocol ExtendsTestWidget)))))

(deftype ExtendersTestWidget [])
(deftest extenders-test
  (reload-example-protocols)
  (testing "a fresh protocol has no extenders"
    (is (nil? (extenders ExampleProtocol))))
  (testing "extending with no methods doesn't count!"
    (deftype Something [])
    (extend ::Something ExampleProtocol)
    (is (nil? (extenders ExampleProtocol))))
  (testing "extending a protocol (and including an impl) adds an entry to extenders"
    (extend ExtendersTestWidget ExampleProtocol {:foo identity})
    (is (= [ExtendersTestWidget] (extenders ExampleProtocol)))))

(deftype SatisfiesTestWidget []
  ExampleProtocol)
(deftest satisifies?-test
  (reload-example-protocols)
  (let [whatzit (SatisfiesTestWidget.)]
    (testing "returns false if a type does not implement the protocol at all"
      (is (false? (satisfies? other/SimpleProtocol whatzit))))
    (testing "returns true if a type implements the protocol directly"
      (is (true? (satisfies? ExampleProtocol whatzit))))
    (testing "returns true if a type explicitly extends protocol"
      (extend
       SatisfiesTestWidget
       other/SimpleProtocol
       {:foo identity})
      (is (true? (satisfies? other/SimpleProtocol whatzit)))))  )

(deftype ReExtendingTestWidget [])
(deftest re-extending-test
  (reload-example-protocols)
  (extend
   ReExtendingTestWidget
   ExampleProtocol
   {:foo (fn [_] "first foo")
    :baz (fn [_] "first baz")})
  (testing "if you re-extend, the old implementation is replaced (not merged!)"
    (extend
     ReExtendingTestWidget
     ExampleProtocol
     {:baz (fn [_] "second baz")
      :bar (fn [_ _] "second bar")})
    (let [whatzit (ReExtendingTestWidget.)]
      (is (thrown? ArgumentException (foo whatzit)))            ;;; IllegalArgumentException
      (is (= "second bar" (bar whatzit nil)))
      (is (= "second baz" (baz whatzit))))))

(defrecord DefrecordObjectMethodsWidgetA [a])
(defrecord DefrecordObjectMethodsWidgetB [a])
(deftest defrecord-object-methods-test
  (testing ".equals depends on fields and type"
    (is (true? (.Equals (DefrecordObjectMethodsWidgetA. 1) (DefrecordObjectMethodsWidgetA. 1))))             ;;; .equals
    (is (false? (.Equals (DefrecordObjectMethodsWidgetA. 1) (DefrecordObjectMethodsWidgetA. 2))))
    (is (false? (.Equals (DefrecordObjectMethodsWidgetA. 1) (DefrecordObjectMethodsWidgetB. 1)))))
  (testing ".hashCode depends on fields and type"
    (is (= (.GetHashCode (DefrecordObjectMethodsWidgetA. 1)) (.GetHashCode (DefrecordObjectMethodsWidgetA. 1))))         ;;; .hashCode
    (is (= (.GetHashCode (DefrecordObjectMethodsWidgetA. 2)) (.GetHashCode (DefrecordObjectMethodsWidgetA. 2))))         ;;; .hashCode
    (is (not= (.GetHashCode (DefrecordObjectMethodsWidgetA. 1)) (.GetHashCode (DefrecordObjectMethodsWidgetA. 2))))      ;;; .hashCode
    (is (= (.GetHashCode (DefrecordObjectMethodsWidgetB. 1)) (.GetHashCode (DefrecordObjectMethodsWidgetB. 1))))         ;;; .hashCode
    (is (not= (.GetHashCode (DefrecordObjectMethodsWidgetA. 1)) (.GetHashCode (DefrecordObjectMethodsWidgetB. 1))))))    ;;; .hashCode

(deftest defrecord-acts-like-a-map
  (let [rec (r 1 2)]
    (is (= (r 1 3 {} {:c 4}) (merge rec {:b 3 :c 4})))))

(deftest defrecord-interfaces-test
  (testing "java.util.Map"
    (let [rec (r 1 2)]
      (is (= 2 (.get_Count rec)))                   ;;; .size
      (is (= 3 (.get_Count (assoc rec :c 3))))      ;;; .size
      ;;;(is (not (.isEmpty rec)))
      ;;;(is (.isEmpty (EmptyRecord.)))
      (is (.Contains rec :a))                    ;;; containsKey
      (is (not (.Contains rec :c)))              ;;; containsKey
      ;;;(is (.containsValue rec 1))
      ;;;(is (not (.containsValue rec 3)))
      (is (= 1 (.get_Item rec :a)))              ;;; .get
      (is (thrown? InvalidProgramException (.set_Item rec :a 1)))      ;;; UnsupportedOperationException
      (is (thrown? InvalidProgramException (.Remove rec :a)))      ;;; UnsupportedOperationException   remove
      ;;;(is (thrown? InvalidProgramException (.putAll rec {})))      ;;; UnsupportedOperationException
      (is (thrown? InvalidProgramException (.Clear rec)))      ;;; UnsupportedOperationException  clear
      (is (= #{:a :b} (.get_Keys rec)))                            ;;; keySet
      (is (= #{1 2} (set (.get_Values rec))))                          ;;; values
      ;;;(is (= #{[:a 1] [:b 2]} (.entrySet rec)))
      
      ))
  (testing "IPersistentCollection"
    (testing ".cons"
      (let [rec (r 1 2)]
        (are [x] (= rec (.cons rec x))
             nil {})
        (is (= (r 1 3) (.cons rec {:b 3})))
        (is (= (r 1 4) (.cons rec [:b 4])))
        (is (= (r 1 5) (.cons rec (MapEntry. :b 5))))))))

(deftest reify-test
  (testing "of an interface"
    (let [s :foo
          r (reify
             System.Collections.IList                                ;;; java.util.List
             (Contains [_ o] (= s o)))]                              ;;; contains
      (testing "implemented methods"
        (is (true? (.Contains r :foo)))                             ;;; contains
        (is (false? (.Contains r :bar))))                           ;;; contains
      (testing "unimplemented methods"
        (is (thrown? System.MissingMethodException (.add r :baz))))))       ;;; AbstractMethodError
  (testing "of two interfaces"
    (let [r (reify
             System.Collections.IList                                ;;; java.util.List
             (Contains [_ o] (= :foo o))                             ;;; contains
             System.Collections.ICollection                          ;;; java.util.Collection
             (get_Count [_] 1))]                                     ;;; (isEmpty [_] false))]
      (is (true? (.Contains r :foo)))                         ;;; contains
      (is (false? (.Contains r :bar)))                         ;;; contains
      (is (= (.get_Count r) 1)) ))                           ;;;(is (false? (.isEmpty r)))))                            ;;; isEmpty
;  (testing "you can't define a method twice"                                          <--  Yes, we can.
;    (is (fails-with-cause?
;         InvalidOperationException #"^Duplicate method name"         ;;; java.lang.ClassFormatError 
;         (eval '(reify
;                 System.Collections.IList                           ;;; java.util.List
;                 (get_Count [_] 10)                                 ;;; size
;                 System.Collections.ICollection                     ;;; java.util.Collection
;                 (get_Count [_] 20))))))                            ;;; size
  (testing "you can't define a method not on an interface/protocol/j.l.Object"
    (is (fails-with-cause? 
         ArgumentException #"^Can't define method not in interfaces: foo"            ;;;  IllegalArgumentException
         (eval '(reify System.Collections.IList (foo [_]))))))                                 ;;; java.util.List
  (testing "of a protocol"
    (let [r (reify
             ExampleProtocol
             (bar [this o] o)
             (baz [this] 1)
             (baz [this o] 2))]
      (= :foo (.bar r :foo))
      (= 1 (.baz r))
      (= 2 (.baz r nil))))
  (testing "destructuring in method def"
    (let [r (reify
             ExampleProtocol
             (bar [this [_ _ item]] item))]
      (= :c (.bar r [:a :b :c]))))
  (testing "methods can recur"
    (let [r (reify
             System.Collections.IList                           ;;; java.util.List
             (get_Item [_ index]                                     ;;; get
                  (if (zero? index)
                    :done
                    (recur (dec index)))))]
      (is (= :done (.get_Item r 0)))                                    ;;; .get
      (is (= :done (.get_Item r 1)))))                                  ;;; .get
  (testing "disambiguating with type hints"
    (testing "you must hint an overloaded method"
      (is (fails-with-cause?
            ArgumentException #"Must hint overloaded method: hinted"               ;;; IllegalArgumentException
            (eval '(reify clojure.test-clojure.protocols.examples.ExampleInterface (hinted [_ o]))))))             ;;; test_clojure
    (testing "hinting"
      (let [r (reify
               ExampleInterface
               (hinted [_ ^int i] (inc i))
               (hinted [_ ^String s] (str s s)))]
        (is (= 2 (.hinted r 1)))
        (is (= "xoxo" (.hinted r "xo")))))))
