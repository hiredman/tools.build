;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.build.tasks.compile-clj
  (:require
    [clojure.java.io :as jio]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [clojure.tools.deps.alpha :as deps]
    [clojure.tools.build.api :as api]
    [clojure.tools.build.util.file :as file]
    [clojure.tools.build.tasks.process :as process]
    [clojure.tools.namespace.find :as find])
  (:import
    [java.io File]
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute]))

(set! *warn-on-reflection* true)

(defn- write-compile-script!
  ^File [^File script-file ^File compile-dir nses compiler-opts]
  (let [script `(do
                  (when-not *compile-files*
                    (binding [~'*compile-path* ~(str compile-dir)
                              ~'*compiler-options* ~compiler-opts]
                      (compile 'your-code)
                      (System/exit 0)))
                  (ns your-code
                    (:require ~@nses)))]
    (spit script-file (with-out-str (pprint/pprint script)))))

(defn- ns->path
  [ns-sym]
  (str/replace (clojure.lang.Compiler/munge (str ns-sym)) \. \/))

(defn compile-clj
  [{:keys [basis src-dirs compile-opts ns-compile filter-nses class-dir] :as params}]
  (let [working-dir (.toFile (Files/createTempDirectory "compile-clj" (into-array FileAttribute [])))]
    (let [{:keys [classpath]} basis
          compile-dir-file (file/ensure-dir (api/resolve-path class-dir))
          nses (or ns-compile
                 (mapcat #(find/find-namespaces-in-dir (api/resolve-path %) find/clj) src-dirs))
          working-compile-dir (file/ensure-dir (jio/file working-dir "compile-clj"))
          compile-script (jio/file working-dir "your_code.clj")
          _ (write-compile-script! compile-script working-compile-dir nses compile-opts)
          cp-str (->> (-> classpath keys (conj (.getPath working-compile-dir) (.getPath compile-dir-file)))
                   (map #(api/resolve-path %))
                   deps/join-classpath)
          _ (spit (jio/file working-dir "compile.cp") cp-str)
          process-args (process/java-command {:basis basis
                                              :main 'clojure.main
                                              :main-args [(.getCanonicalPath compile-script)]})
          exit (:exit (process/process process-args))]
      (if (zero? exit)
        (do
          (if (seq filter-nses)
            (file/copy-contents working-compile-dir compile-dir-file (map ns->path filter-nses))
            (file/copy-contents working-compile-dir compile-dir-file))
          ;; only delete on success, otherwise leave the evidence!
          (file/delete working-dir))
        (throw (ex-info "Clojure compilation failed" {}))))))
