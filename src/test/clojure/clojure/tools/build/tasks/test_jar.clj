;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.test-jar
  (:require
    [clojure.test :refer :all :as test]
    [clojure.java.io :as jio]
    [clojure.tools.build.api :as api]
    [clojure.tools.build.test-util :refer :all]
    [clojure.tools.build.util.zip :as zip]))

(deftest test-jar
  (let [jar-path "target/output.jar"]
    (with-test-dir "test-data/p1"
      (api/set-project-root! (.getAbsolutePath *test-dir*))
      (api/jar {:class-dir "src"
                :jar-file jar-path})
      (is (true? (.exists (jio/file (project-path jar-path)))))
      (is (= #{"META-INF/MANIFEST.MF" "foo/" "foo/bar.clj"}
            (set (map :name (zip/list-zip (project-path jar-path)))))))))

(comment
  (run-tests)
  )
