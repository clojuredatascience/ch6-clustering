#!/bin/bash

script_dir=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
data_dir="${script_dir}/../data"
reuters_url="http://kdd.ics.uci.edu/databases/reuters21578/reuters21578.tar.gz"

mkdir -p "${data_dir}/reuters-sgml"

echo "Downloading ${reuters_url}..."
if [ $(curl -s --head -w %{http_code} $reuters_url -o /dev/null) -eq 200 ]; then
    curl $reuters_url -o "${data_dir}/reuters21578.tar.gz"
    tar xzf "${data_dir}/reuters21578.tar.gz" -C "${data_dir}/reuters-sgml"
else
    echo "Couldn't download data. Perhaps it has moved? Consult http://wiki.clojuredatascience.com"
fi
