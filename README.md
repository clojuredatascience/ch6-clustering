# Clustering

Example code for chapter six, [Clojure for Data Science](https://www.packtpub.com/big-data-and-business-intelligence/clojure-data-science).

## Data

This chapter makes use of the Reuters-21578 text categorization test collection. [See here](http://kdd.ics.uci.edu/databases/reuters21578/README.txt) for more information.

The dataset can be downloaded directly from [here](http://kdd.ics.uci.edu/databases/reuters21578/reuters21578.tar.gz).

## Instructions

### *nix and OS X

Run the following command-line script to download the data to the project's data directory:

```bash
# Downloads and unzips the data files into this project's data directory.
    
script/download-data.sh
```

### Windows / manual instructions

  1. Download the .tar.gz file linked above.
  2. Expand the contents of the file to a directory called data/reuters-sgml inside this project's directory

After following these steps there ought to be many .sgm files inside the data/reuters-sgml directory.

## Running examples

Examples can be run with:
```bash
# Replace 6.1 with the example you want to run:

lein run -e 6.1
```
or open an interactive REPL with:

```bash
lein repl
```
The output of some examples are a prerequisite for subsequent examples. These are aliased as the following, which must be run in order:

```bash
lein extract-reuters

lein create-sequencefile

lein create-vectors
```
The output of `lein create-vectors` is a file that can be used for clustering in Mahout.

## License

Copyright © 2015 Henry Garner

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
