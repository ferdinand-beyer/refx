(ns build
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def base-version "0.0")

(def modules
  {:core {:lib 'com.fbeyer/refx
          :root "core"}
   :http {:lib 'com.fbeyer/refx-http
          :root "http"}})

(defn- git [& args]
  (let [{:keys [exit out]}
        (b/process {:command-args (into ["git"] args)
                    :dir "."
                    :out :capture
                    :err :ignore})]
    (when (and (zero? exit) out)
      (str/trim-newline out))))

(defn- git-tag []
  (git "describe" "--tags" "--exact-match"))

(def tagged  (git-tag))
(def version (if tagged
               (str/replace tagged #"^v" "")
               (format "%s.%s-%s" base-version (b/git-count-revs nil)
                       (if (System/getenv "CI") "ci" "dev"))))

(def repo-url (str "https://github.com/ferdinand-beyer/refx"))

(def scm {:connection (str "scm:git:" repo-url)
          :tag        (or tagged "HEAD")
          :url        repo-url})

(defn- next-tag []
  (format "v%s.%s" base-version (b/git-count-revs nil)))

(defn info [_]
  (println "Version: " version)
  (println "Next tag:" (next-tag)))

(defn tag [_]
  (let [tag (format "v%s.%s" base-version (b/git-count-revs nil))]
    (git "tag" tag)
    (println "Tagged" tag)))

(defn clean "Clean the target directory." [_]
  (b/delete {:path "target"}))

(defn- params [module]
  (let [{:keys [lib root] :as params} (get modules module)
        build-dir (:build-dir params (str "target/" (name lib)))]
    (merge {:build-dir build-dir
            :class-dir (str build-dir "/classes")
            :basis     (b/create-basis {:project (str root "/deps.edn")})
            :src-dir   (str root "/src")
            :jar-file  (str build-dir "/" (name lib) "-" version ".jar")}
           params)))

(defn- jar* [module]
  (let [{:keys [lib class-dir basis src-dir jar-file]} (params module)]
    (b/write-pom {:class-dir class-dir
                  :lib       lib
                  :version   version
                  :basis     basis
                  :src-dirs  [src-dir]
                  :scm       scm})
    (b/copy-dir {:src-dirs   [src-dir]
                 :target-dir class-dir})
    (b/jar {:class-dir class-dir
            :jar-file  jar-file})))

(defn- deploy* [module]
  (let [{:keys [jar-file] :as params} (params module)]
    (dd/deploy {:installer :remote
                :artifact  (b/resolve-path jar-file)
                :pom-file  (b/pom-path (select-keys params [:lib :class-dir]))})))

(defn core-jar [_]
  (jar* :core))

(defn http-jar [_]
  (jar* :http))

(defn jars "Build all Jars." [_]
  (run! jar* (keys modules)))

(defn deploy "Deploy the jars to Clojars." [_]
  (run! deploy* (keys modules)))
