﻿;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:doc "Clojure String utilities

It is poor form to (:use clojure.string). Instead, use require
with :as to specify a prefix, e.g.

(ns your.namespace.here
  (:require [clojure.string :as str]))

Design notes for clojure.string:

1. Strings are objects (as opposed to sequences). As such, the
   string being manipulated is the first argument to a function;
   passing nil will result in a NullPointerException unless
   documented otherwise. If you want sequence-y behavior instead,
   use a sequence.

2. Functions are generally not lazy, and call straight to host
   methods where those are available and efficient.

3. Functions take advantage of String implementation details to
   write high-performing loop/recurs instead of using higher-order
   functions. (This is not idiomatic in general-purpose application
   code.)

4. When a function is documented to accept a string argument, it
   will take any implementation of the correct *interface* on the
   host platform. In Java, this is CharSequence, which is more
   general than String. In ordinary usage you will almost always
   pass concrete strings. If you are doing something unusual,
   e.g. passing a mutable implementation of CharSequence, then
   thread-safety is your responsibility."
      :author "Stuart Sierra, Stuart Halloway, David Liebke"}
  clojure.string
  (:refer-clojure :exclude (replace reverse))
  (:import (System.Text.RegularExpressions Regex MatchEvaluator Match)              ; java.util.regex Pattern
           clojure.lang.LazilyPersistentVector))
(declare re-groups-direct)                                    ;;; I'm going to add a little helper
(defn ^String reverse
  "Returns s with its characters reversed."
  {:added "1.2"}
  [^String s]
  (clojure.lang.RT/StringReverse s))                           ;;; (.toString (.reverse (StringBuilder. s))))

(defn ^String re-quote-replacement
  "Given a replacement string that you wish to be a literal
   replacement for a pattern match in replace or replace-first, do the
   necessary escaping of special characters in the replacement."
  {:added "1.5"}
  [^String replacement]                                           ;;; ^CharSequence
  replacement)                                                    ;;; TODO:  a no-op until I figure out the CLR equivalent -- (Matcher/quoteReplacement (.toString ^CharSequence replacement)))

(defn- replace-by
  [^String s re f]
  (.Replace re s                                                              ;;; (let [m (re-matcher re s)]
     ^MatchEvaluator (gen-delegate MatchEvaluator [m] (f (re-groups-direct m)))))   ;;;    (if (.find m)
                                                                              ;;;      (let [buffer (StringBuffer. (.length s))]
                                                                              ;;;        (loop [found true]
                                                                              ;;;           (if found
                                                                              ;;;            (do (.appendReplacement m buffer (Matcher/quoteReplacement (f (re-groups m))))
                                                                              ;;;                (recur (.find m)))
                                                                              ;;;            (do (.appendTail m buffer)
                                                                              ;;;                (.toString buffer)))))
                                                                              ;;;      s)))

(defn replace
  "Replaces all instance of match with replacement in s.

   match/replacement can be:

   string / string
   char / char
   pattern / (string or function of match).
   
   See also replace-first.

   The replacement is literal (i.e. none of its characters are treated
   specially) for all cases above except pattern / string.

   For pattern / string, $1, $2, etc. in the replacement string are
   substituted with the string that matched the corresponding
   parenthesized group in the pattern.  If you wish your replacement
   string r to be used literally, use (re-quote-replacement r) as the
   replacement argument.  See also documentation for
   java.util.regex.Matcher's appendReplacement method.

   Example:
   (clojure.string/replace \"Almost Pig Latin\" #\"\\b(\\w)(\\w+)\\b\" \"$2$1ay\")
   -> \"lmostAay igPay atinLay\""
  {:added "1.2"}
  [^String s match replacement]
  (cond 
   (instance? Char match) (.Replace s ^Char match ^Char replacement)                         ;;;  Character  .replace
   (instance? String match) (.Replace s ^String match ^String replacement)                   ;;; .replace
   (instance? Regex match) (if (string? replacement)                                         ;;; Pattern
                             (.Replace match s replacement)                                  ;;; (.replaceAll (re-matcher ^Pattern match s) ^String replacement)
                             (replace-by s match replacement))
   :else (throw (ArgumentException. (str "Invalid match arg: " match)))))                 ;;; IllegalArgumentException

(defn- replace-first-by
  "Replace first match of re in s with the result of
  (f (re-groups the-match))."
  [^String s ^Regex re f]                                                       ;;; Pattern
                                                                                ;;; (let [m (re-matcher re s)]
  (.Replace re s                                                                ;;;   (if (.find m)
     ^MatchEvaluator (gen-delegate MatchEvaluator [m] (f (re-groups-direct m)))      ;;;     (let [buffer (StringBuffer. (.length s))
      1))                                                                       ;;;           rep (Matcher/quoteReplacement (f (re-groups m)))]
                                                                                ;;;        (.appendReplacement m buffer rep)
                                                                                ;;;        (.appendTail m buffer)
                                                                                ;;;        (str buffer))
                                                                                ;;;     s)))

(defn- replace-first-char
  [^String s  match replace] (let [match ^Char (char match)]                                   ;;; Character hint on match
  (let [                                                          ;;; s (.toString s)
        i (.IndexOf s match)]                                 ;;; .indexOf (int match)
    (if (= -1 i)
      s
      (str (subs s 0 i) replace (subs s (inc i))))))  )
      
(defn- replace-first-str
  [^String s ^String match ^String replace]                               ;;; ^CharSequence
  (let [                                                                  ;;; ^String s (.toString s)
        i (.IndexOf s match)]                                             ;;; .indexOf
    (if (= -1 i)
      s
      (str (subs s 0 i) replace (subs s (+ i (.Length match)))))))         ;;; .length

(defn replace-first
  "Replaces the first instance of match with replacement in s.

   match/replacement can be:

   char / char
   string / string
   pattern / (string or function of match).

   See also replace.

   The replacement is literal (i.e. none of its characters are treated
   specially) for all cases above except pattern / string.

   For pattern / string, $1, $2, etc. in the replacement string are
   substituted with the string that matched the corresponding
   parenthesized group in the pattern.  If you wish your replacement
   string r to be used literally, use (re-quote-replacement r) as the
   replacement argument.  See also documentation for
   java.util.regex.Matcher's appendReplacement method.

   Example:
   (clojure.string/replace-first \"swap first two words\"
                                 #\"(\\w+)(\\s+)(\\w+)\" \"$3$2$1\")
   -> \"first swap two words\""

  {:added "1.2"}
  [^String s match replacement]
  ;;;(let [s (.toString s)]
    (cond
     (instance? Char match)                                                         ;;; Character
     (replace-first-char s ^Char match replacement)
     (instance? String match)                                                       ;;; CharSequence
     (replace-first-str s match                                                     ;;; (.toString ^CharSequence match)
	                    replacement)                                                ;;; (.toString ^CharSequence replacement)
     (instance? Regex match)                                                        ;;; Pattern
      (if (string? replacement)
       (.Replace ^Regex match s ^String replacement 1)                              ;;; (.replaceFirst (re-matcher ^Pattern match s) ^String replacement)
       (replace-first-by s match replacement))
   :else (throw (ArgumentException. (str "Invalid match arg: " match)))))           ;;; IllegalArgumentException


(defn ^String join
  "Returns a string of all elements in coll, as returned by (seq coll),
  separated by  an optional separator."
  {:added "1.2"}
  ([coll]
     (apply str coll))
  ([separator coll]
     (loop [sb (StringBuilder. (str (first coll)))
            more (next coll)
            sep (str separator)]
       (if more
         (recur (-> sb (.Append sep) (.Append (str (first more))))               ;;; .append
                (next more)
                sep)
         (str sb)))))

(defn ^String capitalize
  "Converts first character of the string to upper-case, all other
  characters to lower-case."
  {:added "1.2"}
  [^String s]
  (if (< (count s) 2)
    (.ToUpper s)                                                        ;;; .toUpperCase
    (str (.ToUpper ^String (subs s 0 1))                                ;;; .toUpperCase
         (.ToLower ^String (subs s 1)))))                               ;;; .toLowerCase

(defn ^String upper-case
  "Converts string to all upper-case."
  {:added "1.2"}
  [^String s]
  (.ToUpper s))                               ;;; .toUpperCase

(defn ^String lower-case
  "Converts string to all lower-case."
  {:added "1.2"}
  [^String s]
  (.ToLower s))                               ;;; .toLowerCase

(defn split
  "Splits string on a regular expression.  Optional argument limit is
  the maximum number of splits. Not lazy. Returns vector of the splits."
  {:added "1.2"}
  ([^String s ^Regex re]                                                   ;;; ^Pattern 
     (LazilyPersistentVector/createOwning (.Split re s)))                  ;;; .split
  ([^String s ^Regex re limit]                                             ;;; ^Pattern 
     (LazilyPersistentVector/createOwning (.Split re s limit))))           ;;; .split
 
(defn split-lines
  "Splits s on \\n or \\r\\n."
  {:added "1.2"}
  [^String s]
  (split s #"\r?\n"))

(defn ^String trim
  "Removes whitespace from both ends of string."
  {:added "1.2"}
  [^String s]
  (.Trim s))                                                                ;;; .trim

(defn ^String triml
  "Removes whitespace from the left side of string."
  {:added "1.2"}
  [^String s]
  (loop [index (int 0)]
    (if (= (.Length s) index)                                       ;;; .length
      ""
      (if (Char/IsWhiteSpace (.get_Chars s index))                      ;;; Character/isWhitespace   .charAt 
        (recur (inc index))
        (.Substring s index)))))                                          ;;; .substring

(defn ^String trimr
  "Removes whitespace from the right side of string."
  {:added "1.2"}
  [^String s]
 (loop [index (.Length s)]                                            ;;; .length
    (if (zero? index)
      ""
      (if (Char/IsWhiteSpace (.get_Chars s (dec index)))                  ;;; Character/isWhitespace   .charAt 
        (recur (dec index))
        (.Substring s 0 index)))))                                    ;;; .substring

(defn ^String trim-newline
  "Removes all trailing newline \\n or return \\r characters from
  string.  Similar to Perl's chomp."
  {:added "1.2"}
  [^String s]
  (loop [index (.Length s)]                                  ;;; .length
    (if (zero? index)
      ""
      (let [ch (.get_Chars s (dec index))]                        ;;; .charAt
        (if (or (= ch \newline) (= ch \return))
          (recur (dec index))
          (.Substring s 0 index))))))                           ;;;  .substring

(defn blank?
  "True if s is nil, empty, or contains only whitespace."
  {:added "1.2"}
  [^String s]                                                      ;;; CharSequence
  (if s
    (loop [index (int 0)]
      (if (= (.Length s) index)                                       ;;; .length
        true
        (if (Char/IsWhiteSpace (.get_Chars s index))                ;;; Character/isWhitespace  .charAt
          (recur (inc index))
          false)))
    true))

(defn ^String escape
  "Return a new string, using cmap to escape each character ch
   from s as follows:
   
   If (cmap ch) is nil, append ch to the new string.
   If (cmap ch) is non-nil, append (str (cmap ch)) instead."
  {:added "1.2"}
  [^String s cmap]                                                              ;;; CharSequence
  (loop [index (int 0)
         buffer (StringBuilder. (.Length s))]                                   ;;; .length
    (if (= (.Length s) index)                                                   ;;; .length
      (.ToString buffer)                                                        ;;; .toString 
      (let [ch (.get_Chars s index)]                                                ;;; .charAt
        (if-let [replacement (cmap ch)]
          (.Append buffer replacement)                                          ;;; .append
          (.Append buffer ch))                                                  ;;; .append
        (recur (inc index) buffer)))))


(defn- re-groups-direct
  "similar to re-groups, but works on a Match directly, rather than JReMatcher"
  [^Match m]
  (let [strs (map #(.Value %) (.Groups ^Match m))
        cnt (count strs)]
	 (if (<= cnt 1) 
	   (first strs)
	   (into [] strs))))
	    